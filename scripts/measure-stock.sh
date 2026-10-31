#!/bin/sh
# Measures the TV's own launcher the same way scripts/measure.sh measures
# Glass Home, so the two can be compared on the same TV: cold start, Home
# (another app, then the launcher again), idle CPU, memory on screen and
# behind another app, and the memory the whole TV has free with each
# launcher on screen.
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
    cpu=$(idle_cpu "$package")
    on_screen=$(pss_mb "$package")
    free_on_screen=$(available_mb)
    shell am start -W -n "$OTHER_APP/.MainSettings" >/dev/null 2>&1
    sleep 15
    behind=$(pss_mb "$package")
    echo "| $cold ms | $warm ms | $cpu% | $on_screen MB | $behind MB | $free_on_screen MB |"
}

front=$(shell dumpsys activity activities | sed -n 's/.*topResumedActivity=ActivityRecord{[^ ]* [^ ]* \([^ ]*\) .*/\1/p' | head -1)
restore() {
    case "$front" in
        "") ;;
        *) shell am start -n "$front" >/dev/null 2>&1 || true ;;
    esac
}
trap restore EXIT

stock=$(stock_home)
stock_package=${stock%%/*}
echo "The TV's own home screen: $stock"

results="$work/stock-vs-glass-home.md"
{
    echo "| Launcher | Cold start | Home | Idle CPU | Memory on screen | Behind another app | Free memory on the TV |"
    echo "|---|---|---|---|---|---|---|"
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
