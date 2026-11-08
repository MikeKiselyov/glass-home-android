#!/bin/sh
# Installs a Glass Home APK on the TV with Glass Home as its own installer, then
# lets it install apps (scripts/install/allow-updates.sh) and set the TV's 12/24-hour
# clock (scripts/install/allow-clock.sh). The installer and allow-updates let Glass Home
# put an update on after you say "Install", without Android's extra questions.
# Android 14 doesn't let adb change the installer later, so it is set here.
#
# Usage: scripts/install/install-apk.sh <apk>
set -eu

root="$(cd "$(dirname "$0")/../.." && pwd)"
PACKAGE=com.glasshome.tv
apk=${1:?usage: scripts/install/install-apk.sh <apk>}
TV=${TV:-$("$root/scripts/tv/connect.sh")}

installed=$(adb -s "$TV" install -r -i "$PACKAGE" "$apk" 2>&1 || true)
case "$installed" in
    *Success*) ;;
    *INSTALL_FAILED_UPDATE_INCOMPATIBLE*) ;;
    *)
        # An Android that refuses the installer name: install the plain way.
        # Updates still work; Android asks once more before each one.
        installed=$(adb -s "$TV" install -r "$apk" 2>&1 || true)
        ;;
esac
case "$installed" in
    *Success*) ;;
    *)
        echo "$installed" >&2
        echo "Could not install $apk. INSTALL_FAILED_UPDATE_INCOMPATIBLE means a build signed with another key is on the TV: uninstall it first (its settings and photos go with it)." >&2
        exit 1
        ;;
esac
TV="$TV" "$root/scripts/install/allow-updates.sh"
TV="$TV" "$root/scripts/install/allow-clock.sh"
