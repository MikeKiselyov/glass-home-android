# Releasing

Releases are APKs signed with one permanent key, so a TV can update from one
release to the next without losing Glass Home's settings and photos.

## Make a release

```sh
scripts/release.sh 0.2.0
```

From a clean `main`, it bumps `versionName` and `versionCode`, runs the tests,
builds the APK signed with the release key, refuses to go on if it is signed
with the debug key, commits "Release 0.2.0", tags `v0.2.0`, pushes both and
publishes a GitHub release with `glass-home.apk` and its SHA-256 attached.

## The key

- `glass-home-release.jks` (RSA 4096, valid 100 years) and
  `keystore.properties` (its path and password) sit in the repository folder
  and are ignored by git. Never commit them.
- **Keep a copy of both somewhere safe** (a password manager, an encrypted
  backup). If the key is lost, no later release can update an installed Glass
  Home: people would have to uninstall it, and its settings and photos with it.
- Certificate SHA-256: `952f6d18b0ce7c4b6c77ae4e00a9b5aac9576c3666ec604a4f9e060440e7afc6`.
  Check an APK with `apksigner verify --print-certs glass-home.apk`.

## Builds from source

Without `keystore.properties`, release builds are signed with the local debug
key. Such a build can't be installed over a release (and the other way round):
uninstall first. `scripts/deploy.sh` says so when it happens.
