#!/bin/sh
# Saves the TV's current state before any change: package states, system
# settings, the Home app and a screenshot. Read-only on the TV.
# Saved to .tv/backup/<time>/ and remembered in .tv/latest-backup.
#
# Usage: scripts/backup.sh
set -eu

root="$(cd "$(dirname "$0")/.." && pwd)"
TV=${TV:-$("$root/scripts/tv-connect.sh")}
backup="$root/.tv/backup/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$backup"

shell() {
    adb -s "$TV" shell "$@" 2>/dev/null | grep -v "Init wrapper" || true
}

shell pm list packages -f -u -U > "$backup/packages-all.txt"
shell pm list packages -e > "$backup/packages-enabled.txt"
shell pm list packages -d > "$backup/packages-disabled.txt"
for namespace in global secure system; do
    shell settings list "$namespace" > "$backup/settings-$namespace.txt"
done
shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME > "$backup/home-activity.txt"
shell getprop ro.product.manufacturer > "$backup/device.txt"
shell getprop ro.product.model >> "$backup/device.txt"
shell getprop ro.build.version.release >> "$backup/device.txt"
adb -s "$TV" shell screencap -p /sdcard/gh-backup.png
adb -s "$TV" pull /sdcard/gh-backup.png "$backup/screen.png" >/dev/null
adb -s "$TV" shell rm /sdcard/gh-backup.png

echo "$backup" > "$root/.tv/latest-backup"
echo "Backup saved to $backup"
