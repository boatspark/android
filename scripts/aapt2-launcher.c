#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

int main(int argc, char **argv) {
    char executable[PATH_MAX];
    ssize_t length = readlink("/proc/self/exe", executable, sizeof(executable) - 1);
    if (length < 0) {
        perror("readlink");
        return 1;
    }
    executable[length] = '\0';

    char *tools = strstr(executable, "/.tools/");
    if (tools == NULL) {
        fputs("aapt2 launcher must be installed under .tools\n", stderr);
        return 1;
    }
    *tools = '\0';

    char qemu[PATH_MAX];
    char sysroot[PATH_MAX];
    char aapt2[PATH_MAX];
    snprintf(qemu, sizeof(qemu), "%s/.tools/qemu/usr/bin/qemu-x86_64", executable);
    snprintf(sysroot, sizeof(sysroot), "%s/.tools/x86-root", executable);
    snprintf(aapt2, sizeof(aapt2), "%s/.tools/android-sdk/build-tools/36.0.0/aapt2", executable);

    char **forwarded = calloc((size_t)argc + 3, sizeof(char *));
    if (forwarded == NULL) {
        fputs("unable to allocate aapt2 arguments\n", stderr);
        return 1;
    }
    forwarded[0] = qemu;
    forwarded[1] = "-L";
    forwarded[2] = sysroot;
    forwarded[3] = aapt2;
    for (int index = 1; index < argc; ++index) {
        forwarded[index + 3] = argv[index];
    }
    forwarded[argc + 3] = NULL;

    execv(qemu, forwarded);
    perror("execv");
    return 1;
}

