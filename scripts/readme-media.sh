#!/bin/sh
# Takes the pictures in docs/images from the real TV: every theme, the hold-OK
# menu, the settings screen on the TV and the "set up from your phone" panel. Also measures the memory each
# theme uses on the home screen, for the table in the README.
#
# Nothing personal goes into the pictures: it pins one bundled photo, shows the
# weather for Grenoble (where that photo was taken), hides the apps in HIDE and
# pauses music, so "now playing" is not shown. Everything it changes is put
# back at the end, and the app that was on screen before is opened again.
#
# The phone panel picture shows the sign-in code, so the code is replaced right
# after it. A new code signs every phone out: they scan the QR code again.
#
# Needs a debug build on the TV (it reads the sign-in code with run-as).
#
# Usage: scripts/readme-media.sh            pictures, then put the TV back
#        scripts/readme-media.sh --keep     pictures, leave the scene set up and
#                                           print a sign-in link for the phone
#                                           website pictures (docs/images/web-*)
#        scripts/readme-media.sh --restore  put the TV back after --keep
set -eu

mode=all
case "${1:-}" in
    "") ;;
    --keep) mode=keep ;;
    --restore) mode=restore ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
esac

root="$(cd "$(dirname "$0")/.." && pwd)"
PACKAGE=com.glasshome.tv
PORT=8090
HIDE="com.surfshark.vpnclient.android"
PHOTO="bundled:nature-07.jpg"
PLACE_NAME="Grenoble, Auvergne-Rhône-Alpes, France"
PLACE_LATITUDE=45.1885
PLACE_LONGITUDE=5.7245
WIDTH=1600

TV=${TV:-$("$root/scripts/tv-connect.sh")}
. "$root/scripts/accessibility.sh"
remember_accessibility
ip=$(adb -s "$TV" shell ip -4 addr show wlan0 2>/dev/null | sed -n 's/.*inet \([0-9.]*\).*/\1/p' | head -1)
if [ -z "$ip" ]; then
    ip=${TV%%:*}
fi
base="http://$ip:$PORT"
work="$root/.tv/media"
images="$root/docs/images"
mkdir -p "$work" "$images"
jar="$work/cookies.txt"

# ---------- helpers ----------

# form name value [name value ...] -> "name=value&..." with every value URL-encoded.
form() {
    python3 - "$@" <<'PY'
import sys
import urllib.parse

arguments = sys.argv[1:]
pairs = []
for index in range(0, len(arguments), 2):
    pairs.append((arguments[index], arguments[index + 1]))
print(urllib.parse.urlencode(pairs))
PY
}

# json_field <file> <key> -> the value, empty when missing.
json_field() {
    python3 - "$1" "$2" <<'PY'
import json
import sys

with open(sys.argv[1]) as file:
    data = json.load(file)
value = data.get(sys.argv[2], "")
if value is None:
    value = ""
print(value)
PY
}

# is_hidden <dock.json> <key> -> "yes" or "no".
is_hidden() {
    python3 - "$1" "$2" <<'PY'
import json
import sys

with open(sys.argv[1]) as file:
    data = json.load(file)
answer = "no"
for item in data["items"]:
    if item["key"] == sys.argv[2] and item["hidden"]:
        answer = "yes"
print(answer)
PY
}

# pref_value <prefs xml> <name> -> a value from SharedPreferences, unescaped.
pref_value() {
    python3 - "$1" "$2" <<'PY'
import sys
import xml.etree.ElementTree as ElementTree

root = ElementTree.parse(sys.argv[1]).getroot()
value = ""
for entry in root:
    if entry.get("name") == sys.argv[2]:
        if entry.tag == "string":
            value = entry.text or ""
        else:
            value = entry.get("value", "")
print(value)
PY
}

api_get() {
    curl -sf -b "$jar" "$base$1"
}

api_post() {
    curl -sf -b "$jar" -c "$jar" -X POST -H "X-Glass-Home: 1" --data "$2" "$base$1" >/dev/null
}

current_code() {
    adb -s "$TV" shell run-as "$PACKAGE" cat shared_prefs/server.xml | sed -n 's/.*name="code">\([0-9]*\)<.*/\1/p'
}

sign_in() {
    code=$(current_code)
    rm -f "$jar"
    if ! api_post /api/login "$(form code "$code")"; then
        echo "Could not sign in to the TV's website with code '$code'." >&2
        return 1
    fi
}

key() {
    adb -s "$TV" shell input keyevent "$@"
}

shot() {
    name=$1
    adb -s "$TV" shell screencap -p /sdcard/gh-media.png
    adb -s "$TV" pull /sdcard/gh-media.png "$work/$name.png" >/dev/null
    adb -s "$TV" shell rm /sdcard/gh-media.png
    sips -s format jpeg -s formatOptions 82 -Z "$WIDTH" "$work/$name.png" --out "$images/$name.jpg" >/dev/null
    echo "  docs/images/$name.jpg"
}

# Saves the screen's view tree to $work/ui.xml; false when uiautomator fails.
dump_screen() {
    for attempt in 1 2 3; do
        if adb -s "$TV" shell uiautomator dump /data/local/tmp/gh-ui.xml 2>/dev/null | grep -q "dumped"; then
            adb -s "$TV" shell cat /data/local/tmp/gh-ui.xml > "$work/ui.xml"
            adb -s "$TV" shell rm -f /data/local/tmp/gh-ui.xml
            return 0
        fi
        sleep 1
    done
    return 1
}

# tap_text "Set up from your phone": taps the middle of the view showing that text.
# uiautomator often misses the first time while a menu slides in: try a few times.
tap_text() {
    for attempt in 1 2 3 4; do
        if tap_text_once "$1"; then
            return 0
        fi
        sleep 2
    done
    return 1
}

tap_text_once() {
    dump_screen || return 1
    point=$(python3 - "$work/ui.xml" "$1" <<'PY'
import re
import sys
import xml.etree.ElementTree as ElementTree

root = ElementTree.parse(sys.argv[1]).getroot()
point = ""
for node in root.iter("node"):
    if node.get("text") == sys.argv[2]:
        numbers = [int(number) for number in re.findall(r"\d+", node.get("bounds", ""))]
        if len(numbers) == 4:
            point = str((numbers[0] + numbers[2]) // 2) + " " + str((numbers[1] + numbers[3]) // 2)
            break
print(point)
PY
)
    if [ -z "$point" ]; then
        return 1
    fi
    adb -s "$TV" shell input tap $point
}

# ---------- putting the TV back ----------

restore() {
    # Every step is tried even if one fails: a half-restored TV is the worst outcome.
    set +e
    echo "Putting the TV back"
    if ! sign_in; then
        echo "The TV still shows the scene for the pictures. Run: scripts/readme-media.sh --restore" >&2
        return 1
    fi
    before_theme=$(json_field "$work/settings.json" theme)
    before_mode=$(json_field "$work/settings.json" photoMode)
    before_pinned=$(json_field "$work/settings.json" pinnedPhoto)
    if [ -n "$before_pinned" ]; then
        api_post /api/settings "$(form theme "$before_theme" photoMode "$before_mode" pinnedPhoto "$before_pinned")" || true
    else
        api_post /api/settings "$(form theme "$before_theme" photoMode "$before_mode")" || true
        echo "  note: no photo was pinned before; $PHOTO stays as the pinned photo (only used in pinned mode)"
    fi

    before_latitude=$(pref_value "$work/weather.xml" latitude)
    if [ -n "$before_latitude" ]; then
        before_longitude=$(pref_value "$work/weather.xml" longitude)
        before_place=$(pref_value "$work/weather.xml" name)
        api_post /api/weather "$(form name "$before_place" latitude "$before_latitude" longitude "$before_longitude")" || true
    else
        echo "  note: no city was set before; turn the weather off on the phone website if you don't want Grenoble" >&2
    fi

    for app in $HIDE; do
        if [ "$(is_hidden "$work/dock.json" "$app")" = no ]; then
            api_post /api/dock "$(form key "$app" hidden 0)" || true
        fi
    done

    key KEYCODE_BACK
    before_front=$(cat "$work/front")
    case "$before_front" in
        "$PACKAGE"/*|"") ;;
        *) adb -s "$TV" shell am start -n "$before_front" >/dev/null ;;
    esac
    # Play once the app is back in front, or the key goes nowhere.
    if [ "$(cat "$work/playing")" = true ]; then
        sleep 2
        key KEYCODE_MEDIA_PLAY
    fi
    rm -f "$jar" "$work/state-saved"
}

if [ "$mode" = restore ]; then
    if [ ! -f "$work/state-saved" ]; then
        echo "Nothing to put back: run with --keep first." >&2
        exit 1
    fi
    restore
    exit 0
fi

# ---------- remember the current state ----------

if [ -f "$work/state-saved" ]; then
    echo "The TV still has the scene from --keep: run --restore first." >&2
    exit 1
fi
sign_in
api_get /api/settings > "$work/settings.json"
api_get /api/dock > "$work/dock.json"
adb -s "$TV" shell run-as "$PACKAGE" cat shared_prefs/weather.xml > "$work/weather.xml"
# Newer Android prints "state=PLAYING(3)", older "state=3".
if adb -s "$TV" shell dumpsys media_session | grep -q -E "state=PlaybackState \{state=(PLAYING|3[,)])"; then
    echo true > "$work/playing"
else
    echo false > "$work/playing"
fi
# Android 10+ names the top activity "topResumedActivity", older "mResumedActivity".
adb -s "$TV" shell dumpsys activity activities | sed -n -E 's/.*(topResumedActivity|mResumedActivity)[=:] ?ActivityRecord\{[^ ]* [^ ]* ([^ ]*) .*/\2/p' | head -1 > "$work/front"
touch "$work/state-saved"
if [ "$mode" = all ]; then
    trap restore EXIT
    trap 'exit 130' INT TERM
fi

# ---------- set the scene ----------

echo "Setting up the TV"
if [ "$(cat "$work/playing")" = true ]; then
    key KEYCODE_MEDIA_PAUSE
fi
for app in $HIDE; do
    api_post /api/dock "$(form key "$app" hidden 1)"
done
api_post /api/settings "$(form photoMode pinned pinnedPhoto "$PHOTO")"
api_post /api/weather "$(form name "$PLACE_NAME" latitude "$PLACE_LATITUDE" longitude "$PLACE_LONGITUDE")"
adb -s "$TV" shell am start -n "$PACKAGE/.MainActivity" >/dev/null
sleep 6

# ---------- the pictures ----------

echo "Taking pictures"
: > "$work/memory.txt"
for theme in clock glass postcard type; do
    api_post /api/settings "$(form theme "$theme")"
    sleep 4
    shot "theme-$theme"
    # Measure a fresh process, so earlier themes don't count.
    adb -s "$TV" shell am force-stop "$PACKAGE"
    put_accessibility_back
    adb -s "$TV" shell am start -n "$PACKAGE/.MainActivity" >/dev/null
    sleep 10
    pss=$(adb -s "$TV" shell dumpsys meminfo "$PACKAGE" | sed -n 's/.*TOTAL PSS: *\([0-9]*\).*/\1/p' | head -1)
    if [ -n "$pss" ]; then
        echo "$theme $((pss / 1024)) MB" >> "$work/memory.txt"
    else
        echo "$theme not measured (the app was not up yet)" >> "$work/memory.txt"
    fi
done
echo "Memory on the home screen (README, Themes):"
sed 's/^/  /' "$work/memory.txt"

api_post /api/settings "$(form theme clock)"
sleep 4
key --longpress KEYCODE_DPAD_CENTER
sleep 2
shot "tile-menu"

if tap_text "Glass Home settings"; then
    sleep 6
    # One step right from the first theme: the focus ring on Glass, the current theme filled.
    key KEYCODE_DPAD_RIGHT
    sleep 1
    shot "tv-settings"
    key KEYCODE_BACK
    sleep 2
else
    echo "  the Glass Home settings item was not found; docs/images/tv-settings.jpg not updated" >&2
fi

key --longpress KEYCODE_DPAD_CENTER
sleep 2

if tap_text "Set up from your phone"; then
    sleep 2
    shot "phone-panel"
    # The picture shows the sign-in code: replace it, so the published code is useless.
    code_in_picture=$(current_code)
    tap_text "New code (signs phones out)" || true
    replaced=false
    for attempt in 1 2 3 4 5; do
        sleep 1
        if [ "$(current_code)" != "$code_in_picture" ]; then
            replaced=true
            break
        fi
    done
    if [ "$replaced" = false ]; then
        echo "The code in docs/images/phone-panel.jpg ($code_in_picture) is still valid: press New code on the TV." >&2
        exit 1
    fi
    sign_in
    echo "  sign-in code replaced (phones scan the QR code again)"
else
    echo "  the phone panel did not open; docs/images/phone-panel.jpg not updated" >&2
fi
key KEYCODE_BACK

if [ "$mode" = keep ]; then
    echo
    echo "The scene stays set up. Website sign-in link (phone pictures):"
    echo "  $base/?code=$code"
    echo "Then: scripts/readme-media.sh --restore"
fi
