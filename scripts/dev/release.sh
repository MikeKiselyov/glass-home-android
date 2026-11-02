#!/bin/sh
# Makes a release: bumps the version, runs the tests, builds the APK signed with
# the release key, checks the signature, commits and tags on main, pushes, and
# publishes a GitHub release with glass-home.apk attached.
#
# Usage: scripts/dev/release.sh <version>      e.g. scripts/dev/release.sh 0.2.0
#
# Needs keystore.properties and the key it points to (both kept out of git; see
# docs/RELEASING.md), the Android SDK (local.properties) and gh, signed in.
set -eu

root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"
version=${1:-}
case "$version" in
    [0-9]*.[0-9]*.[0-9]*) ;;
    *) echo "Usage: scripts/dev/release.sh <version>, e.g. 0.2.0" >&2; exit 2 ;;
esac
tag="v$version"

if [ ! -f keystore.properties ]; then
    echo "No keystore.properties: release builds would be signed with the debug key. See docs/RELEASING.md." >&2
    exit 1
fi
if [ "$(git branch --show-current)" != main ]; then
    echo "Releases are made from main." >&2
    exit 1
fi
if [ -n "$(git status --porcelain)" ]; then
    echo "The working tree has changes; commit or stash them first." >&2
    exit 1
fi
git pull --ff-only -q
if git rev-parse -q --verify "refs/tags/$tag" > /dev/null; then
    echo "$tag already exists." >&2
    exit 1
fi

if [ -z "${JAVA_HOME:-}" ] && [ -d /Library/Java/JavaVirtualMachines/jdk-19.jdk/Contents/Home ]; then
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-19.jdk/Contents/Home
fi

echo "Version $version"
# The same versionCode CI gives this version (scripts/dev/next-version.sh): major*10000 + minor*100 + patch.
major=$(echo "$version" | cut -d. -f1)
minor=$(echo "$version" | cut -d. -f2)
patch=$(echo "$version" | cut -d. -f3)
next_code=$((major * 10000 + minor * 100 + patch))
sed -i.bak -e "s/^\( *versionCode = \)[0-9]*$/\1$next_code/" -e "s/^\( *versionName = \)\"[^\"]*\"$/\1\"$version\"/" app/build.gradle.kts
rm -f app/build.gradle.kts.bak
grep -E "versionCode|versionName" app/build.gradle.kts

echo "Tests"
./gradlew -q :app:testDebugUnitTest

echo "Building"
./gradlew -q clean assembleRelease
apk="$root/app/build/outputs/apk/release/app-release.apk"

echo "Checking the signature"
sdk=$(sed -n 's/^sdk.dir=//p' local.properties)
build_tools=$(ls "$sdk/build-tools" | tail -1)
certificates=$("$sdk/build-tools/$build_tools/apksigner" verify --print-certs "$apk")
echo "$certificates" | sed -n 's/^.*[Ss]igner.*certificate DN: /  /p' | head -1
# "Signer #1 certificate SHA-256 digest: ..." up to build-tools 36, "V2 Signer: certificate SHA-256 digest: ..." from 37.
certificate=$(echo "$certificates" | sed -n 's/^.*[Ss]igner.*certificate SHA-256 digest: //p' | head -1)
# The release key's certificate (docs/RELEASING.md): any other key, the debug key included, can't update a TV.
if [ "$certificate" != "952f6d18b0ce7c4b6c77ae4e00a9b5aac9576c3666ec604a4f9e060440e7afc6" ]; then
    echo "Not signed with the release key ($certificate); stopping." >&2
    git checkout app/build.gradle.kts
    exit 1
fi

out="$root/build/release"
mkdir -p "$out"
cp "$apk" "$out/glass-home.apk"
sum=$(shasum -a 256 "$out/glass-home.apk" | cut -c1-64)

echo "Committing and tagging"
git commit -q -am "Release $version"
git tag -a "$tag" -m "Glass Home $version"
git push -q origin main "$tag"

echo "Publishing"
notes="$out/notes.md"
{
    echo "Install: \`scripts/setup.sh --apk glass-home.apk\` (backs up the TV first, undo with \`scripts/restore.sh\`; \`--only glasshome\` for Glass Home alone), or \`scripts/install/install-apk.sh glass-home.apk\` for the app and nothing else. Step by step: docs/INSTALL.md."
    echo
    echo "Already on 0.4.0 or later? Glass Home finds this release by itself and asks before installing it."
    echo
    echo "SHA-256 of glass-home.apk: \`$sum\`"
} > "$notes"
gh release create "$tag" "$out/glass-home.apk" --title "Glass Home $version" --notes-file "$notes" --generate-notes
echo "Done: $(gh release view "$tag" --json url --jq .url)"
