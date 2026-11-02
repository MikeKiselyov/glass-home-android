#!/bin/sh
# Copies the official icon tiles (.tv/official-icons/tiles/official-*.png, made by
# make-official-icons.py) into Glass Home's private icons folder on the TV.
# Works with the debug build (run-as). Doesn't touch what's on screen: the dock
# picks the icons up the next time it is shown.
#
# Usage: scripts/dev/push-icons.sh        (TV=<serial> to pick a device)
set -eu

root="$(cd "$(dirname "$0")/../.." && pwd)"
TV=${TV:-$("$root/scripts/tv/connect.sh")}
PACKAGE=com.glasshome.tv
tiles="$root/.tv/official-icons/tiles"

adb -s "$TV" shell run-as "$PACKAGE" mkdir -p files/icons
for tile in "$tiles"/official-*.png; do
    name=$(basename "$tile")
    adb -s "$TV" push "$tile" "/data/local/tmp/$name" >/dev/null
    adb -s "$TV" shell "cat /data/local/tmp/$name | run-as $PACKAGE sh -c 'cat > files/icons/$name'"
    adb -s "$TV" shell run-as "$PACKAGE" chmod 600 "files/icons/$name"
    adb -s "$TV" shell rm "/data/local/tmp/$name"
    echo "pushed $name"
done
adb -s "$TV" shell run-as "$PACKAGE" ls -l files/icons
