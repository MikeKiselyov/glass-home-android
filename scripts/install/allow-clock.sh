#!/bin/sh
# Lets Glass Home put its clock setting (12 or 24 hours) on the whole TV, so
# Google's screensaver shows the same clock as the home screen. "Auto" on the
# website leaves the TV's setting alone. Android calls this "Modify system
# settings"; Glass Home writes no other system setting.
# The TV's setting from before is recorded, so scripts/restore.sh puts it back.
#
# Usage: scripts/install/allow-clock.sh
set -eu

root="$(cd "$(dirname "$0")/../.." && pwd)"
PACKAGE=com.glasshome.tv
TV=${TV:-$("$root/scripts/tv/connect.sh")}
. "$root/scripts/lib/changelog.sh"

if ! grep -q "^setting system time_12_24 " "$CHANGES" 2>/dev/null; then
    old=$(tv_shell settings get system time_12_24 | tr -d '\r')
    if [ -z "$old" ] || [ "$old" = "null" ]; then
        old="<absent>"
    fi
    echo "setting system time_12_24 $old" >> "$CHANGES"
fi
tv_shell appops set "$PACKAGE" WRITE_SETTINGS allow >/dev/null
allowed=$(tv_shell appops get "$PACKAGE" WRITE_SETTINGS | tr -d '\r')
case "$allowed" in
    *allow*) echo "Clock: the TV follows Glass Home's 12/24-hour setting." ;;
    *) echo "Could not allow Glass Home to set the TV's clock: $allowed" >&2; exit 1 ;;
esac
