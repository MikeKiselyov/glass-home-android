#!/bin/sh
# Lets Glass Home install its own updates once you say "Install". Without it,
# Android asks "Allow from this source?" and "Update this app?" on top of
# Glass Home's own question. Nothing is ever installed without "Install" on the
# TV or the phone website, and the website can switch update checks off.
# Updates go without Android's questions only when Glass Home is also its own
# installer, which scripts/install/install-apk.sh sets while installing.
# Undone by uninstalling Glass Home; scripts/restore.sh leaves it, like Glass Home.
#
# Usage: scripts/install/allow-updates.sh
set -eu

root="$(cd "$(dirname "$0")/../.." && pwd)"
PACKAGE=com.glasshome.tv
TV=${TV:-$("$root/scripts/tv/connect.sh")}

adb -s "$TV" shell appops set "$PACKAGE" REQUEST_INSTALL_PACKAGES allow
allowed=$(adb -s "$TV" shell appops get "$PACKAGE" REQUEST_INSTALL_PACKAGES | tr -d '\r')
case "$allowed" in
    *allow*) ;;
    *) echo "Could not allow installing apps for Glass Home: $allowed" >&2; exit 1 ;;
esac
installer=$(adb -s "$TV" shell dumpsys package "$PACKAGE" | sed -n 's/^ *installerPackageName=//p' | head -1 | tr -d '\r')
if [ "$installer" = "$PACKAGE" ]; then
    echo "Updates: Glass Home installs them after you say Install."
else
    echo "Updates: Glass Home asks first, then Android asks once more (installed by $installer, not by Glass Home)."
fi
