#!/bin/sh
# Measures Glass Home on the TV: start time, smoothness, idle CPU and memory
# for every theme, the APK size, and the memory of the TV's own launcher for
# comparison. Prints a Markdown table for the README (section "Speed and
# memory") and saves it to .tv/measure/results.md.
#
# Takes over the screen for about 5 minutes; everything it changes (theme,
# the app on screen) is put back at the end.
#
# Needs a debug build on the TV (it reads the sign-in code with run-as).
# Usage: scripts/measure.sh [runs]   (start time runs per theme, default 5)
set -eu

root="$(cd "$(dirname "$0")/.." && pwd)"
PACKAGE=com.glasshome.tv
ACTIVITY="$PACKAGE/.MainActivity"
STOCK_LAUNCHER=com.google.android.apps.tv.launcherx
OTHER_APP=com.android.tv.settings
PORT=8090
runs=${1:-5}

TV=${TV:-$("$root/scripts/tv-connect.sh")}
base="http://${TV%%:*}:$PORT"
work="$root/.tv/measure"
mkdir -p "$work"
jar="$work/cookies.txt"

shell() {
    adb -s "$TV" shell "$@"
}

sign_in() {
    code=$(shell run-as "$PACKAGE" cat shared_prefs/server.xml | sed -n 's/.*name="code">\([0-9]*\)<.*/\1/p')
    rm -f "$jar"
    curl -sf -c "$jar" -X POST -H "X-Glass-Home: 1" --data "code=$code" "$base/api/login" >/dev/null
}

set_theme() {
    curl -sf -b "$jar" -X POST -H "X-Glass-Home: 1" --data "theme=$1" "$base/api/settings" >/dev/null
}

median() {
    sort -n | awk '{ values[NR] = $1 } END { if (NR == 0) { print "-" } else { print values[int((NR + 1) / 2)] } }'
}

pss_mb() {
    kb=$(shell dumpsys meminfo "$1" | sed -n 's/.*TOTAL PSS: *\([0-9]*\).*/\1/p' | head -1)
    if [ -z "$kb" ]; then
        echo "-"
    else
        echo $((kb / 1024))
    fi
}

# "TotalTime: 812" from am start -W, in ms.
start_time() {
    shell am start -W -n "$ACTIVITY" | sed -n 's/.*TotalTime: *\([0-9]*\).*/\1/p'
}

cold_start() {
    shell am force-stop "$PACKAGE"
    sleep 2
    start_time
}

# Home pressed in another app: the activity is still in memory.
warm_start() {
    shell am start -W -n "$OTHER_APP/.MainSettings" >/dev/null 2>&1
    sleep 3
    start_time
}

# Moves along the dock and reports how long 90% of the frames took, from gfxinfo.
# (The "janky frames" share is left out: it depends on how many other frames
# were drawn at the same time, so it can't be compared between versions.)
smoothness() {
    shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null
    for step in 1 2 3 4 5; do
        shell input keyevent KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT
    done
    sleep 1
    info=$(shell dumpsys gfxinfo "$PACKAGE")
    p90=$(echo "$info" | sed -n 's/.*90th percentile: *\([0-9]*\)ms.*/\1/p' | head -1)
    echo "${p90:--}"
}

# CPU used by the app over 30 seconds on the home screen, in % of one core.
idle_cpu() {
    pid=$(shell pidof "$PACKAGE")
    ticks_per_second=100
    first=$(shell cat "/proc/$pid/stat" | awk '{ print $14 + $15 }')
    sleep 30
    second=$(shell cat "/proc/$pid/stat" | awk '{ print $14 + $15 }')
    awk -v used=$((second - first)) -v hz=$ticks_per_second 'BEGIN { printf "%.1f", used / hz / 30 * 100 }'
}

# ---------- remember the current state ----------

# The screensaver starts after 10 minutes without a key press and stops the
# home screen; wake the TV before every theme.
shell input keyevent KEYCODE_WAKEUP

sign_in
before_theme=$(curl -sf -b "$jar" "$base/api/settings" | python3 -c 'import json,sys; print(json.load(sys.stdin)["theme"])')
front=$(shell dumpsys activity activities | sed -n 's/.*topResumedActivity=ActivityRecord{[^ ]* [^ ]* \([^ ]*\) .*/\1/p' | head -1)

restore() {
    # Every step is tried even if one fails: a half-restored TV is the worst outcome.
    set +e
    echo "Putting the TV back"
    # The app may have just been stopped for a cold start: bring its website back first.
    shell am start -n "$ACTIVITY" >/dev/null 2>&1
    sleep 3
    if ! sign_in; then
        echo "Could not sign in to put the theme back ($before_theme); set it on the phone website." >&2
    else
        set_theme "$before_theme"
    fi
    case "$front" in
        "$PACKAGE"/*|"") shell am start -n "$ACTIVITY" >/dev/null ;;
        *) shell am start -n "$front" >/dev/null ;;
    esac
    rm -f "$jar"
}
trap restore EXIT

# ---------- per theme ----------

results="$work/results.md"
{
    echo "| Theme | Cold start | Home | Moving along the dock: 90% of frames under | Idle CPU | Memory on screen | Memory behind another app |"
    echo "|---|---|---|---|---|---|---|"
} > "$results"

for theme in clock glass postcard type; do
    echo "Theme $theme"
    shell input keyevent KEYCODE_WAKEUP
    set_theme "$theme"
    sleep 3

    : > "$work/cold.txt"
    : > "$work/warm.txt"
    for run in $(seq 1 "$runs"); do
        cold_start >> "$work/cold.txt"
        sleep 4
        warm_start >> "$work/warm.txt"
        sleep 2
    done
    # cold_start killed the process: the session cookie is still valid.
    cold=$(median < "$work/cold.txt")
    warm=$(median < "$work/warm.txt")

    sleep 6
    p90=$(smoothness)
    cpu=$(idle_cpu)
    on_screen=$(pss_mb "$PACKAGE")

    shell am start -W -n "$OTHER_APP/.MainSettings" >/dev/null 2>&1 || true
    sleep 15
    behind=$(pss_mb "$PACKAGE")
    shell am start -n "$ACTIVITY" >/dev/null
    sleep 3

    echo "| ${theme} | ${cold} ms | ${warm} ms | ${p90} ms | ${cpu}% | ${on_screen} MB | ${behind} MB |" >> "$results"
    tail -1 "$results"
done

# ---------- the rest ----------

apk="$root/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$apk" ]; then
    (cd "$root" && ./gradlew -q assembleRelease)
fi
apk_mb=$(awk -v bytes="$(wc -c < "$apk")" 'BEGIN { printf "%.1f", bytes / 1048576 }')
stock=$(pss_mb "$STOCK_LAUNCHER")
ram_mb=$(( $(shell cat /proc/meminfo | sed -n 's/MemTotal: *\([0-9]*\).*/\1/p') / 1024 ))
model=$(shell getprop ro.product.model | tr -d '\r')
android=$(shell getprop ro.build.version.release | tr -d '\r')

{
    echo
    echo "- Release APK: $apk_mb MB."
    echo "- The TV's own launcher ($STOCK_LAUNCHER), running in the background: $stock MB."
    echo "- TV: $model, Android $android, $ram_mb MB of RAM. Medians of $runs runs."
} >> "$results"

echo
cat "$results"
