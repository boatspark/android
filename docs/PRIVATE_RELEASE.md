# Private release procedure

## Signing

Create a release keystore outside the repository and protect it as a long-lived
release credential. Copy `keystore.properties.example` to the ignored
`keystore.properties` file and provide the absolute keystore path and aliases.
Never commit either file or print passwords in CI logs.

Build and test:

```bash
scripts/gradlew-local clean \
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
`scripts/gradlew-local :app:assembleRelease
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
