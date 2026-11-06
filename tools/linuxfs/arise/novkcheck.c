/* AriseOS: Eden-family emulators (Orchid) test Vulkan at start by re-running themselves with
 * YUZU_DO_STARTUP_CHECKS=ON and treat any crash of that child as "broken Vulkan". Under FEX in proot the
 * child dies for reasons unrelated to the GPU, so the child ends here at once with success instead. */
#include <stdlib.h>
#include <unistd.h>
__attribute__((constructor)) static void skip_vulkan_check(void) {
    const char *v = getenv("YUZU_DO_STARTUP_CHECKS");
    if (v && v[0] == 'O' && v[1] == 'N' && !v[2]) _exit(0);
}
