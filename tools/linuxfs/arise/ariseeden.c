/* AriseOS preload for Eden-family emulators (Orchid, Eglantine, Lichii) on ARM64 Linux.
 *
 * 1. Log: eden_log.txt is flushed only on errors, so a log shared mid-game stopped at the last error. That one
 *    file is opened line-buffered.
 * 2. Address space: at boot the emulator reserves 256 GiB (1 << 38) of address space at one of 64 "random" hints
 *    between 64 GiB and 256 GiB (common/host_memory.cpp ChooseVirtualBase, std::mt19937_64 with its default seed,
 *    so always the same hints). In the Linux runtime that range is taken by the time a game starts, every hint
 *    failed ("mmap failed: Cannot allocate memory") and the game hung. With ARISE_RESERVE_VA=1 the first free hint
 *    is reserved here before anything else loads, and handed over when the emulator asks for exactly it. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/resource.h>
#include <unistd.h>
#include <sys/types.h>

#ifndef MAP_FIXED_NOREPLACE
#define MAP_FIXED_NOREPLACE 0x100000
#endif

#define VA_SIZE (1ULL << 38)
#define HUGE_PAGE 0x200000ULL

static FILE *tune(FILE *f, const char *path) {
    size_t n = path ? strlen(path) : 0;
    if (f && n >= 12 && !strcmp(path + n - 12, "eden_log.txt"))
        setvbuf(f, NULL, _IOLBF, 0);
    return f;
}

FILE *fopen(const char *path, const char *mode) {
    static FILE *(*real)(const char *, const char *);
    if (!real) real = (FILE *(*)(const char *, const char *))dlsym(RTLD_NEXT, "fopen");
    return tune(real(path, mode), path);
}

FILE *fopen64(const char *path, const char *mode) {
    static FILE *(*real)(const char *, const char *);
    if (!real) real = (FILE *(*)(const char *, const char *))dlsym(RTLD_NEXT, "fopen64");
    return tune(real(path, mode), path);
}

/* std::mt19937_64, default seed 5489 */
static uint64_t mt[312];
static int mti = 313;
static void mt_seed(uint64_t s) {
    mt[0] = s;
    for (mti = 1; mti < 312; mti++)
        mt[mti] = 6364136223846793005ULL * (mt[mti - 1] ^ (mt[mti - 1] >> 62)) + (uint64_t)mti;
}
static uint64_t mt_next(void) {
    static const uint64_t mag[2] = {0ULL, 0xB5026F5AA96619E9ULL};
    uint64_t x;
    if (mti >= 312) {
        int i;
        for (i = 0; i < 312 - 156; i++) {
            x = (mt[i] & 0xFFFFFFFF80000000ULL) | (mt[i + 1] & 0x7FFFFFFFULL);
            mt[i] = mt[i + 156] ^ (x >> 1) ^ mag[(int)(x & 1ULL)];
        }
        for (; i < 311; i++) {
            x = (mt[i] & 0xFFFFFFFF80000000ULL) | (mt[i + 1] & 0x7FFFFFFFULL);
            mt[i] = mt[i + (156 - 312)] ^ (x >> 1) ^ mag[(int)(x & 1ULL)];
        }
        x = (mt[311] & 0xFFFFFFFF80000000ULL) | (mt[0] & 0x7FFFFFFFULL);
        mt[311] = mt[155] ^ (x >> 1) ^ mag[(int)(x & 1ULL)];
        mti = 0;
    }
    x = mt[mti++];
    x ^= (x >> 29) & 0x5555555555555555ULL;
    x ^= (x << 17) & 0x71D67FFFEDA60000ULL;
    x ^= (x << 37) & 0xFFF7EEE000000000ULL;
    x ^= (x >> 43);
    return x;
}

uint64_t arise_hint(int i) {
    const uint64_t lower = (1ULL << 36) / HUGE_PAGE, upper = ((1ULL << 39) - VA_SIZE) / HUGE_PAGE;
    uint64_t v = 0;
    mt_seed(5489ULL);
    for (int k = 0; k <= i; k++) v = mt_next();
    return ((v % (upper - lower)) + lower) * HUGE_PAGE;
}

static void *reserved;
static void *(*real_mmap)(void *, size_t, int, int, int, off_t);

/* Prints what holds the 64 GiB - 512 GiB range, so a failure can be read from the session log. */
static void report(void) {
    FILE *maps = fopen("/proc/self/maps", "r");
    char line[512];
    int shown = 0;
    struct rlimit st;
    getrlimit(RLIMIT_STACK, &st);
    fprintf(stderr, "arise-eden: no free 256 GiB range at any of the 64 places; RLIMIT_STACK=%s\n",
            st.rlim_cur == RLIM_INFINITY ? "unlimited" : "limited");
    while (maps && fgets(line, sizeof line, maps) && shown < 40) {
        unsigned long long a, b;
        if (sscanf(line, "%llx-%llx", &a, &b) != 2 || b <= (1ULL << 36) || a >= (1ULL << 39)) continue;
        fprintf(stderr, "arise-eden: in range %s", line);
        shown++;
    }
    if (maps) fclose(maps);
}

__attribute__((constructor)) static void reserve(void) {
    const char *on = getenv("ARISE_RESERVE_VA");
    if (!on || on[0] != '1') return;
    /* An unlimited stack limit makes the kernel lay out mmap bottom-up from 1/4 of the address space, right
     * through the range the emulator needs. Restart once with the usual 8 MiB limit (top-down layout). */
    struct rlimit st;
    if (getrlimit(RLIMIT_STACK, &st) == 0 && st.rlim_cur == RLIM_INFINITY && !getenv("ARISE_VA_RESTARTED")) {
        st.rlim_cur = 8UL << 20;
        if (setrlimit(RLIMIT_STACK, &st) == 0) {
            char exe[4096];
            ssize_t n = readlink("/proc/self/exe", exe, sizeof exe - 1);
            FILE *cmd = fopen("/proc/self/cmdline", "r");
            static char buf[65536];
            size_t len = cmd ? fread(buf, 1, sizeof buf - 1, cmd) : 0;
            if (cmd) fclose(cmd);
            if (n > 0 && len > 0) {
                char *argv[256];
                int argc = 0;
                exe[n] = 0;
                for (size_t i = 0; i < len && argc < 255; i += strlen(buf + i) + 1) argv[argc++] = buf + i;
                argv[argc] = NULL;
                setenv("ARISE_VA_RESTARTED", "1", 1);
                fprintf(stderr, "arise-eden: stack limit was unlimited (bottom-up mmap layout); restarting with 8 MiB\n");
                execv(exe, argv);
            }
        }
    }
    real_mmap = (void *(*)(void *, size_t, int, int, int, off_t))dlsym(RTLD_NEXT, "mmap");
    if (!real_mmap) return;
    for (int i = 0; i < 64; i++) {
        void *want = (void *)(uintptr_t)arise_hint(i);
        void *got = real_mmap(want, VA_SIZE, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE | MAP_FIXED_NOREPLACE, -1, 0);
        if (got == want) {
            reserved = got;
            fprintf(stderr, "arise-eden: holding the emulator's 256 GiB range at %p (place %d of 64)\n", got, i + 1);
            return;
        }
        if (got != MAP_FAILED) munmap(got, VA_SIZE);
    }
    report();
}

void *mmap(void *addr, size_t len, int prot, int flags, int fd, off_t off) {
    if (!real_mmap) real_mmap = (void *(*)(void *, size_t, int, int, int, off_t))dlsym(RTLD_NEXT, "mmap");
    if (reserved && addr == reserved && len == VA_SIZE && !(flags & MAP_FIXED)) {
        void *got = real_mmap(addr, len, prot, flags | MAP_FIXED, fd, off);
        if (got == addr) reserved = NULL;
        return got;
    }
    return real_mmap(addr, len, prot, flags, fd, off);
}
void *mmap64(void *addr, size_t len, int prot, int flags, int fd, off_t off) __attribute__((alias("mmap")));
