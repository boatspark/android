# BoatSpark Android

Native Android client for BoatSpark boat status and alert data.

## Local setup

Builds use the devcontainer approach from
[`adrianba/android-builder`](https://github.com/adrianba/android-builder).
The JDK 21 and Android SDK (API 37, build-tools 37.0.0) run inside Docker,
with this checkout bind-mounted so artifacts and reports appear on the host.
Supported hosts are x86-64 Linux and WSL; ARM64 is not supported.

Install Docker CE and Node.js/npm on the host, ensure `docker info` succeeds,
and install the pinned devcontainer CLI:

```bash
npm install --global @devcontainers/cli@0.90.0
./scripts/check-android-env
./scripts/android --no-daemon :app:assembleDebug
```

The first invocation builds and starts the container; later invocations reuse
it. The wrapper works from any directory and returns Gradle's exit status.
No host JDK, Android SDK, or Gradle installation is needed. In WSL, keep the
checkout in the Linux filesystem rather than under `/mnt/c`.

Copy `local.properties.example` to `local.properties`, then replace the API,
Pocket ID issuer, and public OIDC client ID values. When migrating an existing
`local.properties`, remove any `sdk.dir` entry pointing to the old host SDK;
leave application settings intact. The container supplies `ANDROID_HOME`.
The Pocket ID client must be
registered as a public native client with this exact callback URI:

```text
com.boatspark.app:/oauth2redirect
```

Required scopes are `openid email profile groups`. Do not configure a client
secret in the app.

Common commands:

```bash
./scripts/android :app:assembleDebug
./scripts/android :app:testDebugUnitTest
./scripts/android :app:lintDebug
```

Compose and Room instrumentation tests are packaged with:

```bash
./scripts/android :app:assembleDebugAndroidTest
```

They require a connected physical device or emulator to execute. A USB
device attached to the host can be used from the container (see
[Physical devices](#physical-devices)):

```bash
./scripts/adb devices
./scripts/android :app:connectedDebugAndroidTest
./scripts/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

See
[`docs/PRIVATE_RELEASE.md`](docs/PRIVATE_RELEASE.md) for signing and private
distribution, and [`docs/PUSH_NOTIFICATIONS.md`](docs/PUSH_NOTIFICATIONS.md)
for the future true-push design.

The map uses MapLibre with OpenStreetMap and OpenSeaMap community tile
endpoints for light private use. It does not download offline map packs and is
not intended for navigation. Review the current
[OpenStreetMap tile policy](https://operations.osmfoundation.org/policies/tiles/)
and [OpenSeaMap terms](https://www.openseamap.org/) before each release.

## Publishing safely

Before committing or publishing:

- Review `git status --short --ignored`; do not use a blind bulk add.
- Confirm `local.properties`, `keystore.properties`, signing keys, `.env`
  files, downloaded tools, build directories, APKs, and AABs remain ignored.
- Keep only placeholder values in checked-in configuration examples.
- Never add an OIDC client secret. The native client ID and callback URI are
  public identifiers, not credentials.
- Run GitHub secret scanning after publication and report vulnerabilities
  privately as described in [`SECURITY.md`](SECURITY.md).

## License and branding

BoatSpark source code and documentation are available under the
[MIT License](LICENSE). The BoatSpark name, logos, launcher icons, and derived
brand artwork are excluded from that license; see
[`BRANDING.md`](BRANDING.md).

## Container maintenance and CI

After changing `.devcontainer/Dockerfile` or container configuration, rebuild:

```bash
devcontainer up --workspace-folder . --remove-existing-container
./scripts/check-android-env
```

The Java base image is digest-pinned and the Android command-line tools archive
is version-pinned and SHA-256 verified. Update these pins deliberately alongside
the environment checks. The Gradle cache lives inside the container and may be
lost on rebuild; generated APKs and reports remain under `app/build/`.
Old ignored `.tools/` downloads are no longer used and are not deleted by the
migration.

GitHub Actions does not use the container. For speed it uses the runner's
preinstalled Android SDK with Temurin 21 and Gradle caching, and runs the same
tasks as the local validation command:

```bash
./scripts/android --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Copilot CLI runs on the host; repository instructions direct builds into the
container. Copilot authentication remains on the host and the container does
not need a Docker socket mount. This configuration does not install or start
an emulator; accelerated emulators would need host KVM configuration.

### Physical devices

The devcontainer bind-mounts the host `/dev/bus/usb`, allows USB character
devices (`--device-cgroup-rule=c 189:* rmw`) and adds the non-root `vscode`
user to group id 46, which is `plugdev` on Debian/Ubuntu hosts. If your host
uses a different group for Android USB nodes, adjust `--group-add` in
`.devcontainer/devcontainer.json`. The container's `~/.android`, holding the
ADB key and debug signing keystore, is the named Docker volume
`android-adb-keys`. Other Android devcontainers can mount the same volume, so
the phone only needs to approve one key across projects and rebuilds. The
host must provide `/dev/bus/usb`; on a Linux host without it, run
`sudo mkdir -p /dev/bus/usb` before starting the container.

Run ADB through `./scripts/adb`. Only one ADB server can claim a device, so
stop ADB on the host or in other containers first. The container has no udev
and does not notice hotplug; after replugging the phone run:

```bash
./scripts/adb kill-server && ./scripts/adb start-server && ./scripts/adb devices
```

`connectedDebugAndroidTest` uninstalls the app when it finishes.
