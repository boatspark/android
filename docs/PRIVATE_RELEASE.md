# Private release procedure

## Signing

Create a release keystore outside the repository and protect it as a long-lived
release credential. Copy `keystore.properties.example` to the ignored
`keystore.properties` file and provide the container-visible keystore path and aliases.
Never commit either file or print passwords in CI logs.

An absolute host path is not automatically available inside the container.
For an external key, add an explicit read-only mount to your local
`.devcontainer/devcontainer.json`, for example:

```json
"mounts": [
  "source=/absolute/host/path/boatspark-release.jks,target=/run/boatspark-release.jks,type=bind,readonly"
]
```

Keep this machine-specific edit uncommitted, rebuild the container as described
in the README, and use `storeFile=/run/boatspark-release.jks` in the ignored
`keystore.properties`. Ensure the non-root container user can read the key
without making it world-readable. Do not mount your entire home directory.
Relative `storeFile` paths are resolved from the `app` module inside the
container. CI validates debug builds only and does not provision signing keys.

Build and test:

```bash
./scripts/android clean \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:assembleDebug \
  :app:assembleRelease
sha256sum app/build/outputs/apk/release/app-release.apk
```

With signing configured, the release artifact is
`app/build/outputs/apk/release/app-release.apk`. The build deliberately fails
without all four signing values so an unsigned APK cannot be mistaken for a
release. To inspect minification without release credentials, explicitly run
`./scripts/android :app:assembleRelease
-Pboatspark.allowUnsignedRelease=true`; that artifact is named
`app-release-unsigned.apk` and cannot be installed.

Record the distributable APK's version, SHA-256 checksum, signing-certificate
fingerprint, API/OIDC environment, and installation audience.

## Configuration checklist

- Register Pocket ID client `com.boatspark.app` as a public native client.
- Register callback `com.boatspark.app:/oauth2redirect` exactly.
- Grant `openid email profile groups`; do not create an app client secret.
- Set HTTPS API base URL, issuer URL, and client ID in `local.properties` or CI.
- Confirm users belong to `boatspark_users` or `boatspark_admin`.
- Re-check OpenStreetMap tile policy and OpenSeaMap terms.
- Keep map endpoints configurable and verify visible attribution.
- Validate login, token refresh, offline cache, one-minute foreground refresh,
  background refresh, logout data removal, and map display on a physical phone.

## Installation and rollback

Distribute the APK and checksum through the approved private channel. Android
updates must use the same application ID and signing key. Retain the prior APK
and its checksum; rollback by reinstalling that signed version through the same
management channel. Android may require uninstalling before a version-code
downgrade, which deletes local cached data but not server data.
