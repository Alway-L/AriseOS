/* AriseOS: Eden-family emulators write their log (eden_log.txt) through stdio and flush it only on errors,
 * so a log shared while a game runs stops at the last error. Opening that file line-buffered writes each
 * line as it comes. Everything else is left as it is. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdio.h>
#include <string.h>

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
