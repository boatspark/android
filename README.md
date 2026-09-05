# BoatSpark Android

Native Android client for BoatSpark boat status and alert data.

## Local setup

The project keeps its JDK and Android SDK under the ignored `.tools/`
directory. On Linux ARM64 and x86-64, bootstrap them and run Gradle through the
local wrapper:

```bash
scripts/bootstrap-toolchain
scripts/gradlew-local :app:assembleDebug
```

The wrapper ensures the local toolchain and ARM64 build compatibility are
configured consistently.

Copy `local.properties.example` to `local.properties`, then replace the API,
Pocket ID issuer, and public OIDC client ID values. The Pocket ID client must be
registered as a public native client with this exact callback URI:

```text
com.boatspark.app:/oauth2redirect
```

Required scopes are `openid email profile groups`. Do not configure a client
secret in the app.

Common commands:

```bash
scripts/gradlew-local :app:assembleDebug
scripts/gradlew-local :app:testDebugUnitTest
scripts/gradlew-local :app:lintDebug
```

Compose and Room instrumentation tests are packaged with:

```bash
scripts/gradlew-local :app:assembleDebugAndroidTest
```

They require a connected physical device or emulator to execute. See
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

## Global installation

No global JDK, Android SDK, or Gradle installation is required for
command-line builds. The bootstrap script expects standard host utilities:
`bash`, `curl`, `tar`, and `unzip`.

On ARM64 Linux, Google's current `aapt2` is x86-64. The script additionally
requires `cc`, `apt`, and `dpkg-deb`; it downloads QEMU and the required x86
runtime into `.tools/`, then builds a small local launcher. Nothing is installed
system-wide. An Android emulator may require host-level virtualization/KVM
configuration; a physical device avoids that host change.
