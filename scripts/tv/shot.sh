#!/bin/sh
# Takes a screenshot of the TV and saves a 960px copy for quick review.
# Usage: scripts/tv/shot.sh <name>   (saved to .tv/shots/<name>.png)
set -eu

root="$(cd "$(dirname "$0")/../.." && pwd)"
TV=${TV:-$("$root/scripts/tv/connect.sh")}
name=${1:-shot}
dir="$(cd "$(dirname "$0")/../.." && pwd)/.tv/shots"
mkdir -p "$dir"
adb -s "$TV" shell screencap -p /sdcard/gh-shot.png
adb -s "$TV" pull /sdcard/gh-shot.png "$dir/$name.png" >/dev/null
adb -s "$TV" shell rm /sdcard/gh-shot.png
sips -Z 960 "$dir/$name.png" --out "$dir/$name.small.png" >/dev/null
echo "$dir/$name.small.png"
