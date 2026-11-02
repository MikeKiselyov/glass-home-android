# Releasing

Releases are APKs signed with one permanent key, so a TV can update from one
release to the next without losing Glass Home's settings and photos.

## Releases happen on merge

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) runs the unit tests on
every pull request and every push to `main`. After a merge to `main` that
changes the app (`app/`, the Gradle files; not docs or scripts) and passes the
tests, it publishes a release by itself:

1. The version is the next patch after the newest tag (`v0.4.0` -> `0.4.1`), or
   the `versionName` in `app/build.gradle.kts` when that is higher: raise it in
   the pull request for a bigger release (`0.5.0`). `scripts/dev/next-version.sh`
   prints what the next one will be.
2. `versionCode` is `major*10000 + minor*100 + patch` (0.4.1 -> 401), so it grows
   with the version. Both go to Gradle as `-PreleaseVersionName` and
   `-PreleaseVersionCode`; nothing is committed back to `main`.
3. It builds the APK with the release key, refuses any other certificate,
   tags `v0.4.1` on the merged commit and publishes `glass-home.apk` with its
   SHA-256 and the merged pull requests as notes.

Merging to `main` is therefore publishing: every TV on 0.4.0 or later is
offered the release within a day.

The key lives in the repository's Actions secrets: `RELEASE_KEYSTORE_BASE64`
(the `.jks` in base64), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD`. Set from this folder:

```sh
base64 -i glass-home-release.jks | gh secret set RELEASE_KEYSTORE_BASE64
sed -n 's/^storePassword=//p' keystore.properties | gh secret set RELEASE_STORE_PASSWORD
sed -n 's/^keyAlias=//p' keystore.properties | gh secret set RELEASE_KEY_ALIAS
sed -n 's/^keyPassword=//p' keystore.properties | gh secret set RELEASE_KEY_PASSWORD
```

## By hand

```sh
scripts/dev/release.sh 0.5.0
```

From a clean `main`, it sets `versionName` and `versionCode` (the same formula),
runs the tests, builds the APK signed with the release key, refuses to go on if
it is signed with the debug key, commits "Release 0.5.0", tags `v0.5.0`, pushes
both and publishes a GitHub release with `glass-home.apk`. The commit then
starts CI, which finds no app change since the tag and publishes nothing more.

## How TVs find it

Glass Home asks `api.github.com/repos/MikeKiselyov/glass-home-android/releases/latest`
once a day. It takes a release only when:

- it is not a draft or a pre-release (a pre-release is a way to publish
  without offering it to TVs),
- the tag is a version (`v0.4.0`) newer than the one installed,
- it has an asset named exactly `glass-home.apk` from this repository's
  releases, with the SHA-256 GitHub lists for it.

Then it downloads the APK, checks the SHA-256, and asks on the home screen
before installing. Keep the asset name and the release key: a TV can't
install an APK signed with any other key over the one it has.

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
uninstall first. `scripts/install/deploy.sh` says so when it happens.
