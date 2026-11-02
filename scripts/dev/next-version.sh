#!/bin/sh
# Prints the version the next release gets and its versionCode, e.g. "0.4.1 401".
#
# The next patch after the newest v* tag (v0.4.0 -> 0.4.1), unless
# app/build.gradle.kts names a higher version by hand (0.5.0 for a bigger
# release). versionCode is major*10000 + minor*100 + patch, so it always grows
# with the version and a TV can install each release over the one before.
#
# Usage: scripts/dev/next-version.sh [tag]   (default: the newest v* tag)
set -eu

root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"

latest_tag=${1:-$(git tag --list 'v*' --sort=-v:refname | head -1)}
declared=$(sed -n 's/^ *versionName = "\([0-9.]*\)"$/\1/p' app/build.gradle.kts)

next=$declared
if [ -n "$latest_tag" ]; then
    latest=${latest_tag#v}
    major=$(echo "$latest" | cut -d. -f1)
    minor=$(echo "$latest" | cut -d. -f2)
    patch=$(echo "$latest" | cut -d. -f3)
    bumped="$major.$minor.$((patch + 1))"
    # The higher of the bumped tag and the declared version.
    highest=$(printf '%s\n%s\n' "$bumped" "$declared" | sort -t. -k1,1n -k2,2n -k3,3n | tail -1)
    next=$highest
fi

major=$(echo "$next" | cut -d. -f1)
minor=$(echo "$next" | cut -d. -f2)
patch=$(echo "$next" | cut -d. -f3)
code=$((major * 10000 + minor * 100 + patch))
echo "$next $code"
