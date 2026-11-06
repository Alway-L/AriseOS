#!/usr/bin/env python3
"""AriseOS patches for the Eden-family Switch emulators AriseOS builds (Orchid, Lichii, Lichii Frizti).

Run from the root of an upstream checkout. Every patch is idempotent and is skipped, with a note, when the code it
changes is not there (an upstream update may have fixed or moved it), so a new upstream tag builds with whatever
still applies. Keep the patches small and anchored on stable code, so updates keep merging cleanly.
"""
import os
import sys

applied, skipped = [], []


def patch(path, marker, old, new, why):
    if not os.path.isfile(path):
        skipped.append(f"{path}: missing ({why})")
        return
    text = open(path, encoding="utf-8").read()
    if marker in text:
        applied.append(f"{path}: already patched ({why})")
        return
    if old not in text:
        skipped.append(f"{path}: anchor not found ({why})")
        return
    open(path, "w", encoding="utf-8").write(text.replace(old, new, 1))
    applied.append(f"{path}: {why}")


# 1. The 256 GiB address block is tried at 64 fixed "random" places; in the phone's Linux runtime they can all be
#    taken, the emulator ran without fastmem and Switch 2 games hung or ran at 0 FPS. Let the kernel pick a place.
patch(
    "src/common/host_memory.cpp", "AriseOS: none of the 64",
    "    return MAP_FAILED;\n}\n",
    """    // AriseOS: none of the 64 places was free (the phone's Linux runtime); let the kernel choose one.
    void* any = mmap(nullptr, virtual_size, PROT_READ | PROT_WRITE,
                     MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE | MAP_NOCORE, -1, 0);
    if (any != MAP_FAILED) {
        LOG_WARNING(HW_Memory, "AriseOS: no free place among the 64 hints; using {} chosen by the kernel", any);
    }
    return any;
}
""",
    "address-space fallback for the 256 GiB block",
)

# 1b. Even the kernel could not find a free 256 GiB block once Qt, the Vulkan driver and the 12 GiB guest memory
#     were mapped (Orchid 1.3.30 log: "mmap failed" at the virtual base, no fastmem, game hung at Launching...).
#     Reserve the block (PROT_NONE, nothing committed) when the program loads, while the address space is still
#     empty, and hand that exact block to the emulator; on release it goes back to the reservation for the next game.
patch(
    "src/common/host_memory.cpp", "AriseOS: early 256 GiB",
    "#ifndef MAP_NOCORE\n#define MAP_NOCORE 0\n#endif\n",
    """#ifndef MAP_NOCORE
#define MAP_NOCORE 0
#endif

// AriseOS: early 256 GiB reservation (see tools/emulators/arise_patches.py). Zero-initialised globals, so the
// constructor below cannot be undone by later dynamic initialisation.
static constexpr size_t AriseReserveSize = 1ULL << 38;
static uintptr_t g_arise_reserved = 0;
static bool g_arise_reserved_in_use = false;
#ifdef ARCHITECTURE_arm64
__attribute__((constructor)) static void AriseReserveEarly() {
    const size_t span = AriseReserveSize + HugePageSize;
    void* p = mmap(nullptr, span, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE, -1, 0);
    if (p == MAP_FAILED) {
        return;
    }
    const uintptr_t start = reinterpret_cast<uintptr_t>(p);
    const uintptr_t aligned = (start + HugePageSize - 1) & ~(uintptr_t(HugePageSize) - 1);
    if (aligned > start) {
        munmap(p, aligned - start);
    }
    const uintptr_t end = start + span;
    if (end > aligned + AriseReserveSize) {
        munmap(reinterpret_cast<void*>(aligned + AriseReserveSize), end - (aligned + AriseReserveSize));
    }
    g_arise_reserved = aligned;
}
#endif
static void* AriseTakeReserved(size_t size) {
    if (g_arise_reserved == 0 || g_arise_reserved_in_use || size > AriseReserveSize) {
        return MAP_FAILED;
    }
    void* p = mmap(reinterpret_cast<void*>(g_arise_reserved), size, PROT_READ | PROT_WRITE,
                   MAP_FIXED | MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE | MAP_NOCORE, -1, 0);
    if (p == MAP_FAILED) {
        return MAP_FAILED;
    }
    g_arise_reserved_in_use = true;
    LOG_INFO(HW_Memory, "AriseOS: using the 256 GiB block reserved at start ({})", p);
    return p;
}
static int AriseUnmapVirtual(void* base, size_t size) {
    if (g_arise_reserved_in_use && reinterpret_cast<uintptr_t>(base) == g_arise_reserved) {
        void* p = mmap(base, AriseReserveSize, PROT_NONE, MAP_FIXED | MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE, -1, 0);
        g_arise_reserved_in_use = false;
        if (p == MAP_FAILED) {
            g_arise_reserved = 0;
            return munmap(base, size);
        }
        return 0;
    }
    return munmap(base, size);
}
""",
    "reserve the 256 GiB block at program start",
)
patch(
    "src/common/host_memory.cpp", "AriseUnmapVirtual(virtual_map_base",
    "int ret = munmap(virtual_map_base, virtual_size);",
    "int ret = AriseUnmapVirtual(virtual_map_base, virtual_size);",
    "give the reserved block back on release",
)
patch(
    "src/common/host_memory.cpp", "AriseOS: try the reserved block first",
    "        virtual_base = virtual_map_base = static_cast<u8*>(ChooseVirtualBase(virtual_size));\n",
    """        // AriseOS: try the reserved block first
        virtual_base = virtual_map_base = static_cast<u8*>(AriseTakeReserved(virtual_size));
        if (virtual_base == MAP_FAILED) {
            virtual_base = virtual_map_base = static_cast<u8*>(ChooseVirtualBase(virtual_size));
        }
""",
    "use the reserved block for the virtual base",
)

# 2. Say why a ZBIC (zstd) decompression of game code failed, instead of only "got -1".
patch(
    "src/common/zbic_compression.cpp", "AriseOS: ZBIC",
    "    if (ZSTD_isError(res)) {\n        return -1;",
    "    if (ZSTD_isError(res)) {\n"
    "        std::fprintf(stderr, \"AriseOS: ZBIC decompression failed: %s (src %zu bytes, dst %zu bytes)\\n\",\n"
    "                     ZSTD_getErrorName(res), src.size(), dst.size());\n"
    "        return -1;",
    "log the ZBIC decompression error",
)
zbic = "src/common/zbic_compression.cpp"
if os.path.isfile(zbic):
    t = open(zbic, encoding="utf-8").read()
    if "AriseOS: ZBIC" in t and "#include <cstdio>" not in t:
        open(zbic, "w", encoding="utf-8").write(t.replace("#include <cstring>", "#include <cstdio>\n#include <cstring>", 1))

# 3. On Linux, vulkan.h pulls in Xlib.h, whose error-code macros (Success, BadValue, ...) clash with enum members
#    (NvResult::Success) in files that include Vulkan first. Forks built only for Windows trip on it.
codes = ("Success BadRequest BadValue BadWindow BadPixmap BadAtom BadCursor BadFont BadMatch BadDrawable BadAccess "
         "BadAlloc BadColor BadGC BadIDChoice BadName BadLength BadImplementation").split()
patch(
    "src/video_core/vulkan_common/vulkan.h", "AriseOS: X11 error codes",
    "#undef CreateEvent\n",
    "#undef CreateEvent\n// AriseOS: X11 error codes clash with enum members (NvResult::Success, NvResult::BadValue).\n"
    + "".join(f"#undef {c}\n" for c in codes),
    "undefine X11 error-code macros",
)

for line in applied:
    print("applied:", line)
for line in skipped:
    print("skipped:", line)
summary = os.environ.get("GITHUB_STEP_SUMMARY")
if summary:
    with open(summary, "a", encoding="utf-8") as f:
        f.write("### AriseOS patches\n" + "".join(f"- applied: {l}\n" for l in applied) + "".join(f"- skipped: {l}\n" for l in skipped))
# The address-space fallback is the one that matters for running games; fail the build without it.
if not any("address-space fallback" in l for l in applied):
    sys.exit("the address-space fallback did not apply: check src/common/host_memory.cpp upstream")
