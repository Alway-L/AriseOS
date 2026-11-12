// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

// SPDX-FileCopyrightText: Copyright 2019 yuzu Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

#include <chrono>
#include <thread>

#include "common/assert.h"
#include "common/dkb_cpu_trace.h"
#include "common/nx2_timeline.h"
#include "common/scope_exit.h"
#include "common/settings.h"
#include "common/thread.h"
#include "core/core.h"
#include <string>
#include <fmt/format.h>
#include "common/logging.h"
#include "core/memory.h"
#include "core/hle/kernel/kernel.h"
#include "core/hle/kernel/k_thread.h"
#include "core/hle/kernel/k_process.h"
#include "core/frontend/graphics_context.h"
#include "video_core/nx2_debug_control.h"
#include "video_core/control/scheduler.h"
#include "video_core/dma_pusher.h"
#include "video_core/gpu.h"
#include "video_core/gpu_thread.h"
#include "video_core/gpu_split.h"
#include "video_core/nx2_profile.h"
#include "video_core/host1x/host1x.h"
#include "video_core/host1x/syncpoint_manager.h"
#include "video_core/renderer_base.h"

#if defined(__linux__) && !defined(__ANDROID__)
#include <atomic>
#include <cerrno>
#include <csignal>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <dirent.h>
#include <dlfcn.h>
#include <execinfo.h>
#include <sys/syscall.h>
#include <unistd.h>
#define ARISE_HOST_STACKS 1
#endif

namespace VideoCommon::GPUThread {

namespace {

// Tells a CPU thread waiting in PushCommand that its command ran.
void SignalCommand(SynchState& state, u64 fence, bool block) {
    state.signaled_fence.store(fence);
    if (block) {
        // We have to lock the write_lock to ensure that the condition_variable wait not get a
        // race between the check and the lock itself.
        std::scoped_lock lk{state.write_lock};
        state.cv.notify_all();
    }
}

#ifdef ARISE_HOST_STACKS
// AriseOS: a frozen game whose GPU threads never go idle gets no hang report above. A watchdog
// logs where each renderer thread is (module + offset per frame) once frames stop for 8 s.
struct AriseHostStack {
    std::atomic<int> count{-1};
    void* frames[40];
};
AriseHostStack g_arise_host_stack;

void AriseHostStackHandler(int) {
    const int saved = errno;
    g_arise_host_stack.count.store(backtrace(g_arise_host_stack.frames, 40),
                                   std::memory_order_release);
    errno = saved;
}

void AriseLogHostStacks() {
    const int signal_number = SIGRTMIN + 6;
    static const bool installed = [signal_number] {
        void* warm[2];
        (void)backtrace(warm, 2); // loads the unwinder outside the handler
        struct sigaction action {};
        action.sa_handler = AriseHostStackHandler;
        action.sa_flags = SA_RESTART;
        sigemptyset(&action.sa_mask);
        return sigaction(signal_number, &action, nullptr) == 0;
    }();
    if (!installed) {
        return;
    }
    DIR* const dir = opendir("/proc/self/task");
    if (dir == nullptr) {
        return;
    }
    const pid_t self = static_cast<pid_t>(syscall(SYS_gettid));
    while (const dirent* entry = readdir(dir)) {
        const pid_t tid = static_cast<pid_t>(std::atoi(entry->d_name));
        if (tid <= 0 || tid == self) {
            continue;
        }
        std::string name;
        std::getline(std::ifstream{fmt::format("/proc/self/task/{}/comm", tid)}, name);
        if (!name.starts_with("GPU") && !name.starts_with("Vulkan") && !name.starts_with("VkPipeline")) {
            continue;
        }
        g_arise_host_stack.count.store(-1, std::memory_order_release);
        if (syscall(SYS_tgkill, getpid(), tid, signal_number) != 0) {
            continue;
        }
        int count = -1;
        for (int wait = 0; wait < 200 && count < 0; ++wait) {
            std::this_thread::sleep_for(std::chrono::milliseconds{1});
            count = g_arise_host_stack.count.load(std::memory_order_acquire);
        }
        std::string text;
        for (int i = 0; i < count; ++i) {
            Dl_info info{};
            void* const frame = g_arise_host_stack.frames[i];
            if (dladdr(frame, &info) != 0 && info.dli_fname != nullptr) {
                const char* const slash = std::strrchr(info.dli_fname, '/');
                text += fmt::format(" {}+{:#x}", slash != nullptr ? slash + 1 : info.dli_fname,
                                    static_cast<u64>(static_cast<const char*>(frame) -
                                                     static_cast<const char*>(info.dli_fbase)));
                if (info.dli_sname != nullptr) {
                    text += fmt::format("({})", info.dli_sname);
                }
            } else {
                text += fmt::format(" {}", fmt::ptr(frame));
            }
        }
        LOG_WARNING(HW_GPU, "AriseOS host stack: thread {} ({}), {} frames:{}", name, tid, count, text);
    }
    closedir(dir);
}

void AriseStartFreezeWatchdog() {
    static std::jthread watchdog{[](std::stop_token stop) {
        Common::SetCurrentThreadName("AriseWatchdog");
        u64 last_presents = 0;
        int still = 0;
        int reports = 0;
        while (!stop.stop_requested() && reports < 3) {
            std::this_thread::sleep_for(std::chrono::seconds{1});
            const u64 presents = Core::g_nx2_present_count.load(std::memory_order_relaxed);
            if (presents != last_presents || presents == 0) {
                last_presents = presents;
                still = 0;
                continue;
            }
            if (++still == 8) {
                ++reports;
                LOG_WARNING(HW_GPU, "AriseOS freeze report {}: no new frame for 8 s after {} presents",
                            reports, presents);
                AriseLogHostStacks();
            }
        }
    }};
}
#endif

// AriseOS: no new frame for 20 s while the GPU thread waits for work. Logs every guest thread of
// the game (state, registers at its last interrupt) and, for runnable ones, the code around the PC
// and the memory its registers point at, so a guest loop waiting on something can be read back.
void AriseHangCheck(Core::System& system, const GpuSplit& split, bool pending_channels) {
    static u64 last_presents = ~u64{0};
    static auto last_change = std::chrono::steady_clock::now();
    static auto last_report = std::chrono::steady_clock::time_point{};
    static int reports = 0;
    const u64 presents = Core::g_nx2_present_count.load(std::memory_order_relaxed);
    const auto now = std::chrono::steady_clock::now();
    if (presents != last_presents) {
        last_presents = presents;
        last_change = now;
        return;
    }
    if (now - last_change < std::chrono::seconds{20} || now - last_report < std::chrono::seconds{60} ||
        reports >= 3) {
        return;
    }
    last_report = now;
    ++reports;
    auto* const process = system.Kernel().ApplicationProcess();
    LOG_WARNING(HW_GPU, "AriseOS hang report {}: no new frame for {} s after {} presents; GPU idle, "
                "channels waiting on a semaphore: {}",
                reports, std::chrono::duration_cast<std::chrono::seconds>(now - last_change).count(),
                presents, pending_channels);
    split.LogState();
    {
        // Guest value = signalled by the command lists; host value = its fence passed on the host GPU.
        // A host value behind the guest one names a fence that never completed.
        const auto& syncpoints = system.Host1x().GetSyncpointManager();
        std::string text;
        for (u32 id = 0; id < Tegra::Host1x::SyncpointManager::NUM_MAX_SYNCPOINTS; ++id) {
            const u32 guest = syncpoints.GetGuestSyncpointValue(id);
            const u32 host = syncpoints.GetHostSyncpointValue(id);
            if (guest != 0 || host != 0) {
                text += fmt::format(" {}={}/{}", id, guest, host);
            }
        }
        LOG_WARNING(HW_GPU, "AriseOS hang report: syncpoints (id=guest/host):{}", text);
    }
    if (process == nullptr) {
        return;
    }
    auto& memory = system.ApplicationMemory();
    for (const auto& thread : process->GetThreadList()) {
        const auto& ctx = thread.GetContext();
        const auto state = static_cast<u32>(thread.GetState());
        LOG_WARNING(HW_GPU, "AriseOS hang report: thread {} state {:#x} core {} prio {} wait {} pc {:#x} "
                    "lr {:#x} sp {:#x} fp {:#x}",
                    thread.GetThreadId(), state, thread.GetActiveCore(), thread.GetPriority(),
                    static_cast<u32>(thread.GetWaitReasonForDebugging()), ctx.pc, ctx.lr, ctx.sp, ctx.fp);
        if ((state & static_cast<u32>(Kernel::ThreadState::Mask)) !=
            static_cast<u32>(Kernel::ThreadState::Runnable)) {
            continue;
        }
        std::string regs;
        for (size_t i = 0; i < ctx.r.size(); ++i) {
            regs += fmt::format(" x{}={:x}", i, ctx.r[i]);
        }
        LOG_WARNING(HW_GPU, "AriseOS hang report: thread {} regs{}", thread.GetThreadId(), regs);
        std::string code;
        for (s64 i = -16; i < 16; ++i) {
            const u64 at = ctx.pc + i * 4;
            code += memory.IsValidVirtualAddressRange(at, 4) ? fmt::format(" {:08x}", memory.Read32(at)) : " ????????";
        }
        LOG_WARNING(HW_GPU, "AriseOS hang report: thread {} code from {:#x}:{}", thread.GetThreadId(),
                    ctx.pc - 64, code);
        // Callers: walk the frame-pointer chain, with the code before each return address.
        u64 frame = ctx.fp;
        u64 ret = ctx.lr;
        for (int depth = 0; depth < 6 && ret != 0; ++depth) {
            std::string around;
            for (s64 i = -24; i < 4; ++i) {
                const u64 at = ret + i * 4;
                around += memory.IsValidVirtualAddressRange(at, 4) ? fmt::format(" {:08x}", memory.Read32(at)) : " ????????";
            }
            LOG_WARNING(HW_GPU, "AriseOS hang report: thread {} caller {} returns to {:#x}, code from {:#x}:{}",
                        thread.GetThreadId(), depth, ret, ret - 96, around);
            if (frame < 0x10000 || !memory.IsValidVirtualAddressRange(frame, 16)) {
                break;
            }
            ret = memory.Read64(frame + 8);
            frame = memory.Read64(frame);
        }
        for (size_t i = 0; i < ctx.r.size(); ++i) {
            const u64 at = ctx.r[i] & ~u64{3};
            if (at < 0x10000 || !memory.IsValidVirtualAddressRange(at, 16)) {
                continue;
            }
            LOG_WARNING(HW_GPU, "AriseOS hang report: thread {} [x{} {:#x}] = {:08x} {:08x} {:08x} {:08x}",
                        thread.GetThreadId(), i, at, memory.Read32(at), memory.Read32(at + 4),
                        memory.Read32(at + 8), memory.Read32(at + 12));
        }
    }
}

} // Anonymous namespace

ThreadManager::ThreadManager(Core::System& system_)
    : system{system_}
{}

ThreadManager::~ThreadManager() = default;

void ThreadManager::StartThread(VideoCore::RendererBase& renderer, Core::Frontend::GraphicsContext& context, Tegra::Control::Scheduler& scheduler) {
    rasterizer = renderer.ReadRasterizer();
#ifdef ARISE_HOST_STACKS
    AriseStartFreezeWatchdog();
#endif
    thread = std::jthread([&](std::stop_token stop_token) {
        const Common::DkbCpuTrace::Registration cpu_trace{Common::DkbCpuTrace::Role::GpuCommand};
        Common::SetCurrentThreadName("GPU");
        Common::SetCurrentThreadPriority(Common::ThreadPriority::Critical);
        Common::PinCurrentThread(Common::PinnedRole::GpuFront);
        Common::SetCurrentThreadToPerformanceCores();
        system.RegisterHostThread();

        auto current_context = context.Acquire();
        CommandDataContainer next;
        // When a pending channel last moved or a command arrived (see the polling below).
        auto last_progress = std::chrono::steady_clock::now();
        namespace Profile = VideoCommon::Nx2Profile;
        // Before the split's back thread starts, which reads it too.
        Profile::Initialize();
        auto& split = VideoCommon::GpuSplit::Get();
        while (!stop_token.stop_requested()) {
            Profile::Report();
            split.MaybeStart(system);
            if (scheduler.HasPendingChannels()) {
                // Suspended channels wait on semaphores the CPU releases: poll them meanwhile.
                {
                    Profile::Scope scope{Profile::Zone::ResumePending};
                    scheduler.ResumePending(system.GPU());
                }
                if (!state.queue.TryPop(next)) {
                    // EDEN_NX2_EXP27 & 1: what the resumed channels sent goes to the back before
                    // this thread yields or sleeps (Commit publishes every 256 words).
                    if (split.Active()) {
                        split.PublishBeforeWait();
                    }
                    // The CPU usually releases the semaphore soon after: poll for half a
                    // millisecond before sleeping, or every wait also waits out a host sleep
                    // (title 1 waits ~25 times a second).
                    // EDEN_NX2_EXP6 (zortos293/Lichii#446): 1 = 50 us sleeps (Ocarina waits here
                    // ~330 times a second and its present waits for the work behind them; on
                    // Windows a sleep still lasts one timer tick, ~0.5 ms), 2 = always yield.
                    Profile::Scope scope{Profile::Zone::PendingSleep};
                    const int poll = VideoCore::Nx2Debug::Experiment(6);
                    if (poll == 2 || std::chrono::steady_clock::now() - last_progress <
                                         std::chrono::microseconds{500}) {
                        std::this_thread::yield();
                    } else if (poll == 1) {
                        std::this_thread::sleep_for(std::chrono::microseconds{50});
                    } else {
                        std::this_thread::sleep_for(std::chrono::milliseconds{1});
                    }
                    continue;
                }
            } else {
                Profile::Scope scope{Profile::Zone::Idle};
                // AriseOS: wait in 1 s slices, so a game that stops producing frames while the GPU
                // has nothing to do is reported (what each guest thread is doing) in the log.
                while (!state.queue.PopWaitFor(next, std::chrono::seconds{1})) {
                    if (stop_token.stop_requested()) {
                        break;
                    }
                    AriseHangCheck(system, split, scheduler.HasPendingChannels());
                }
            }
            if (stop_token.stop_requested()) {
                break;
            }
            last_progress = std::chrono::steady_clock::now();
            if (split.Active()) {
                // Only command lists are parsed here; everything else runs on the back thread
                // in order, and so does the signal that the command ran.
                auto* const rasterizer_ptr = renderer.ReadRasterizer();
                if (auto* submit_list = std::get_if<SubmitListCommand>(&next.data)) {
                    const bool timeline = Common::Nx2Timeline::Active();
                    if (timeline) {
                        // The list's sequence number (PushCommand's "submit list") on each
                        // stage: here, and from a one-entry marker where the back reaches it.
                        Common::Nx2Timeline::Instant("front: list begin", {"seq", next.fence},
                                                     {"channel", submit_list->channel});
                        split.Defer(
                            [seq = next.fence] {
                                Common::Nx2Timeline::Instant("back: list", {"seq", seq});
                            },
                            VideoCommon::GpuSplit::Effect::None);
                    }
                    {
                        Profile::Scope scope{Profile::Zone::Push};
                        scheduler.Push(system.GPU(), submit_list->channel,
                                       std::move(submit_list->entries));
                    }
                    if (timeline) {
                        Common::Nx2Timeline::Instant("front: list end", {"seq", next.fence});
                    }
                } else if (std::holds_alternative<GPUTickCommand>(next.data)) {
                    split.Defer([gpu = &system.GPU()] { gpu->TickWork(); });
                } else if (auto* run = std::get_if<RunCommand>(&next.data)) {
                    if (run->before_later_reads) {
                        // GPU memory maps unmap caches (UnmapMemory downloads): the back's state.
                        split.DeferFunction(std::move(run->func));
                        split.Drain(VideoCommon::DrainSite::Map);
                    } else {
                        split.DeferFunction(std::move(run->func));
                        split.NoteMemoryWrite();
                    }
                } else if (const auto* flush = std::get_if<FlushRegionCommand>(&next.data)) {
                    split.Defer([rasterizer_ptr, addr = flush->addr, size = flush->size] {
                        rasterizer_ptr->FlushRegion(addr, size);
                    });
                } else if (const auto* invalidate = std::get_if<InvalidateRegionCommand>(&next.data)) {
                    split.Defer([rasterizer_ptr, addr = invalidate->addr, size = invalidate->size] {
                        rasterizer_ptr->OnCacheInvalidation(addr, size);
                    });
                } else {
                    ASSERT(false);
                }
                split.Defer(
                    [state_ptr = &state, fence = next.fence, block = next.block] {
                        SignalCommand(*state_ptr, fence, block);
                    },
                    VideoCommon::GpuSplit::Effect::None);
                split.Publish();
                continue;
            }
            if (auto* submit_list = std::get_if<SubmitListCommand>(&next.data)) {
                const bool timeline = Common::Nx2Timeline::Active();
                if (timeline) {
                    Common::Nx2Timeline::Instant("front: list begin", {"seq", next.fence},
                                                 {"channel", submit_list->channel});
                }
                {
                    Profile::Scope scope{Profile::Zone::Push};
                    scheduler.Push(system.GPU(), submit_list->channel,
                                   std::move(submit_list->entries));
                }
                if (timeline) {
                    Common::Nx2Timeline::Instant("front: list end", {"seq", next.fence});
                }
            } else if (std::holds_alternative<GPUTickCommand>(next.data)) {
                Profile::Scope scope{Profile::Zone::Tick};
                system.GPU().TickWork();
            } else if (auto* run = std::get_if<RunCommand>(&next.data)) {
                Profile::Scope scope{Profile::Zone::Run};
                run->func();
            } else if (const auto* flush = std::get_if<FlushRegionCommand>(&next.data)) {
                Profile::Scope scope{Profile::Zone::FlushInvalidate};
                renderer.ReadRasterizer()->FlushRegion(flush->addr, flush->size);
            } else if (const auto* invalidate = std::get_if<InvalidateRegionCommand>(&next.data)) {
                Profile::Scope scope{Profile::Zone::FlushInvalidate};
                renderer.ReadRasterizer()->OnCacheInvalidation(invalidate->addr, invalidate->size);
            } else {
                ASSERT(false);
            }
            SignalCommand(state, next.fence, next.block);
        }
    });
}

void ThreadManager::SubmitList(s32 channel, Tegra::CommandList&& entries, bool is_async) {
    PushCommand(SubmitListCommand(channel, std::move(entries)), false, is_async);
}

void ThreadManager::FlushRegion(DAddr addr, u64 size, bool is_async) {
    if (!is_async) {
        // Always flush with synchronous GPU mode
        PushCommand(FlushRegionCommand(addr, size), false, is_async);
    }
}

void ThreadManager::TickGPU(bool is_async) {
    PushCommand(GPUTickCommand(), false, is_async);
}

void ThreadManager::Run(std::function<void()>&& func, bool is_async, bool before_later_reads) {
    PushCommand(RunCommand{std::move(func), before_later_reads}, false, is_async);
}

void ThreadManager::InvalidateRegion(DAddr addr, u64 size) {
    rasterizer->OnCacheInvalidation(addr, size);
}

void ThreadManager::FlushAndInvalidateRegion(DAddr addr, u64 size, bool is_async) {
    if (VideoCore::Nx2Debug::AccuratePart(8)) {
        if (!is_async) {
            PushCommand(FlushRegionCommand(addr, size), false, is_async);
        } else {
            auto& gpu = system.GPU();
            const u64 fence = gpu.RequestFlush(addr, size);
            TickGPU(is_async);
            gpu.WaitForSyncOperation(fence);
        }
    }
    rasterizer->OnCacheInvalidation(addr, size);
}

u64 ThreadManager::PushCommand(CommandData&& command_data, bool block, bool is_async) {
    if (!is_async) {
        // In synchronous GPU mode, block the caller until the command has executed
        block = true;
    }

    std::unique_lock lk(state.write_lock);
    const u64 fence{++state.last_fence};
    if (Common::Nx2Timeline::Active()) {
        // The command's sequence number, which the GPU threads' list markers repeat
        // (tools/nx2/critical_path.py follows a list through the stages with it). This is also
        // what wakes the front from "idle".
        if (const auto* list = std::get_if<SubmitListCommand>(&command_data)) {
            Common::Nx2Timeline::Instant("submit list", {"seq", fence}, {"channel", list->channel},
                                         {"entries", list->entries.command_lists.size()});
        } else {
            Common::Nx2Timeline::Instant("submit command", {"seq", fence});
        }
    }
    state.queue.EmplaceWait(std::move(command_data), fence, block);

    if (block) {
        // With the split, SignalCommand runs on the back.
        Common::Nx2Timeline::ArgSpan timeline_span{"guest: wait for GPU command", {"seq", fence}};
        state.cv.wait(lk, thread.get_stop_token(), [this, fence] {
            return fence <= state.signaled_fence.load(std::memory_order_relaxed);
        });
    }

    return fence;
}

void ThreadManager::NotifyShutdown() {
    if (thread.joinable()) {
        thread.request_stop();
        thread.join();
    }
    VideoCommon::GpuSplit::Get().Stop();
}

} // namespace VideoCommon::GPUThread
