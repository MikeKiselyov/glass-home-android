#!/bin/sh
# Measures the TV's own launcher the same way scripts/measure.sh measures
# Glass Home, so the two can be compared on the same TV: cold start, Home
# (another app, then the launcher again), idle CPU, memory on screen and
# behind another app, the memory the whole TV has free with each launcher on
# screen, and how smooth each one moves: the same arrow keys on both, then the
# frame times from gfxinfo (half of the frames under, 90% under, and the share
# Android counts as janky).
#
# Glass Home's Home override would put Glass Home in front of the stock home
# screen, so it is switched off while measuring and put back afterwards.
#
# The numbers describe the TV as it is now (after any clean-up with
# scripts/debloat.sh), not as it came from the shop.
#
# Usage: scripts/measure-stock.sh [runs]   (default 5)
set -eu

root="$(cd "$(dirname "$0")/.." && pwd)"
GLASS_HOME=com.glasshome.tv
GLASS_HOME_ACTIVITY="$GLASS_HOME/.MainActivity"
OTHER_APP=com.android.tv.settings
runs=${1:-5}

TV=${TV:-$("$root/scripts/tv-connect.sh")}
work="$root/.tv/measure"
mkdir -p "$work"

shell() {
    adb -s "$TV" shell "$@"
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

available_mb() {
    kb=$(shell cat /proc/meminfo | sed -n 's/MemAvailable: *\([0-9]*\).*/\1/p')
    echo $((kb / 1024))
}

start_time() {
    shell am start -W -n "$1" | sed -n 's/.*TotalTime: *\([0-9]*\).*/\1/p'
}

# Moves around the home screen and prints "p50 p90 janky%" from gfxinfo.
smoothness() {
    shell dumpsys gfxinfo "$1" reset >/dev/null
    for step in 1 2 3 4 5; do
        shell input keyevent KEYCODE_DPAD_DOWN KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_UP
    done
    sleep 2
    info=$(shell dumpsys gfxinfo "$1")
    p50=$(echo "$info" | sed -n 's/.*50th percentile: *\([0-9]*\)ms.*/\1/p' | head -1)
    p90=$(echo "$info" | sed -n 's/.*90th percentile: *\([0-9]*\)ms.*/\1/p' | head -1)
    janky=$(echo "$info" | sed -n 's/.*Janky frames: *[0-9]* (\([0-9.]*\)%).*/\1/p' | head -1)
    echo "${p50:--} ${p90:--} ${janky:--}"
}

idle_cpu() {
    pid=$(shell pidof "$1" | awk '{ print $1 }')
    first=$(shell cat "/proc/$pid/stat" | awk '{ print $14 + $15 }')
    sleep 30
    second=$(shell cat "/proc/$pid/stat" | awk '{ print $14 + $15 }')
    awk -v used=$((second - first)) 'BEGIN { printf "%.1f", used / 100 / 30 * 100 }'
}

# The activity Home opens on this TV (Glass Home itself is skipped).
stock_home() {
    shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME \
        | tr -d '\r' | grep "/" | grep -v "$GLASS_HOME" | head -1 | tr -d ' '
}

# measure <package> <activity>: one table row.
measure() {
    package=$1
    activity=$2
    : > "$work/cold.txt"
    : > "$work/warm.txt"
    for run in $(seq 1 "$runs"); do
        shell input keyevent KEYCODE_WAKEUP
        shell am force-stop "$package"
        sleep 2
        start_time "$activity" >> "$work/cold.txt"
        sleep 4
        shell am start -W -n "$OTHER_APP/.MainSettings" >/dev/null 2>&1
        sleep 3
        start_time "$activity" >> "$work/warm.txt"
        sleep 2
    done
    cold=$(median < "$work/cold.txt")
    warm=$(median < "$work/warm.txt")
    sleep 15
    set -- $(smoothness "$package")
    frames="$1 ms / $2 ms / $3%"
    sleep 5
    cpu=$(idle_cpu "$package")
    on_screen=$(pss_mb "$package")
    free_on_screen=$(available_mb)
    shell am start -W -n "$OTHER_APP/.MainSettings" >/dev/null 2>&1
    sleep 15
    behind=$(pss_mb "$package")
    echo "| $cold ms | $warm ms | $cpu% | $on_screen MB | $behind MB | $free_on_screen MB | $frames |"
}

front=$(shell dumpsys activity activities | sed -n 's/.*topResumedActivity=ActivityRecord{[^ ]* [^ ]* \([^ ]*\) .*/\1/p' | head -1)
accessibility_services=$(shell settings get secure enabled_accessibility_services | tr -d '\r')
restore() {
    case "$accessibility_services" in
        *com.glasshome.tv*) shell settings put secure enabled_accessibility_services "$accessibility_services" ;;
    esac
    case "$front" in
        "") ;;
        *) shell am start -n "$front" >/dev/null 2>&1 || true ;;
    esac
}
trap restore EXIT
trap 'exit 130' INT TERM
case "$accessibility_services" in
    *com.glasshome.tv*)
        others=$(printf "%s" "$accessibility_services" | tr ':' '\n' | grep -v com.glasshome.tv | paste -sd: -)
        if [ -z "$others" ]; then
            shell settings delete secure enabled_accessibility_services >/dev/null
        else
            shell settings put secure enabled_accessibility_services "$others"
        fi
        ;;
esac

stock=$(stock_home)
stock_package=${stock%%/*}
echo "The TV's own home screen: $stock"

results="$work/stock-vs-glass-home.md"
{
    echo "| Launcher | Cold start | Home | Idle CPU | Memory on screen | Behind another app | Free memory on the TV | Frames: 50% / 90% under, janky |"
    echo "|---|---|---|---|---|---|---|---|"
} > "$results"

echo "Measuring $stock_package"
echo "| $stock_package $(measure "$stock_package" "$stock")" >> "$results"
tail -1 "$results"

echo "Measuring Glass Home"
echo "| Glass Home $(measure "$GLASS_HOME" "$GLASS_HOME_ACTIVITY")" >> "$results"
tail -1 "$results"

{
    echo
    echo "- Glass Home theme: $(shell run-as "$GLASS_HOME" cat shared_prefs/settings.xml 2>/dev/null | sed -n 's/.*name="theme">\([a-z]*\)<.*/\1/p')."
    echo "- Packages switched off on this TV: $(shell pm list packages -d | grep -c package:)."
    echo "- Medians of $runs runs."
} >> "$results"
echo
cat "$results"
