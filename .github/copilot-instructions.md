# Android development

Copilot CLI runs on the Linux/WSL host. The Android toolchain runs only in this
repository's devcontainer, using Docker on an x86-64 host.

Run all Gradle commands through `./scripts/android`. Never run `./gradlew`
directly on the host or install a host JDK, Gradle, or Android SDK.
The wrapper starts or reuses the container and propagates command failures.

Validate the environment with:

```sh
./scripts/check-android-env
```

Validate application changes with the same tasks as CI:

```sh
./scripts/android --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Instrumentation tests can be packaged with `:app:assembleDebugAndroidTest`.
A USB phone attached to the host is reachable from the container: run ADB only
through `./scripts/adb` (never host `adb`), and run tests with
`./scripts/android :app:connectedDebugAndroidTest`. After a replug, restart
the container's ADB server (`./scripts/adb kill-server`). Do not claim
instrumentation tests passed unless they actually ran.

Keep signing credentials and machine-specific `local.properties` out of Git.
Do not add `sdk.dir` pointing to a host SDK; use the container's Android SDK.
See `docs/PRIVATE_RELEASE.md` for container-visible signing paths.
Report failed or unavailable checks explicitly.
