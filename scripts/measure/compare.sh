#!/bin/sh
# Before and after on your own TV: run "before" on the TV as it is, set it up
# (scripts/setup.sh), run "after", and get one table. Measures whatever home
# screen the TV shows: its own launcher before, Glass Home after.
#
# Usage: scripts/measure/compare.sh before [runs]   (default 3)
#        scripts/measure/compare.sh after [runs]
#        scripts/measure/compare.sh table            prints the table again
#
# Per run: the home screen in front, 20 s quiet, then free memory, the home
# screen's memory (every process of its package), process counts, and over 30 s
# the CPU of the home screen and of the whole TV; then TV Settings in front for
# 15 s and the home screen's memory behind it; then Home again (am start -W).
# Once per state: frame times while moving with the arrow keys, and for Glass
# Home the time from the Home key to its first frame (scripts/measure/home-key.sh).
#
# For a fair comparison, reboot before each state, or don't reboot at all.
# The numbers go to .tv/compare/<state>/; nothing on the TV is changed.
set -eu

root="$(cd "$(dirname "$0")/../.." && pwd)"
state=${1:-}
runs=${2:-3}
case "$state" in
    before|after|table) ;;
    *) sed -n '2,19p' "$0"; exit 2 ;;
esac
out="$root/.tv/compare"
GLASS_HOME=com.glasshome.tv
GOOGLE_TV=com.google.android.apps.tv.launcherx
OTHER_APP=com.android.tv.settings/.MainSettings

median() {
    sort -n | awk '{ values[NR] = $1 } END { if (NR == 0) print "-"; else print values[int((NR + 1) / 2)] }'
}

# value <state> <column>: the median of one column of that state's runs.
value() {
    file="$out/$1/runs.tsv"
    if [ ! -f "$file" ]; then
        echo "-"
        return
    fi
    tail -n +2 "$file" | cut -f "$2" | median
}

# with_unit <value> <unit>: "542 MB", or "-" when there is no number.
with_unit() {
    if [ "$1" = "-" ]; then
        echo "-"
    else
        echo "$1$2"
    fi
}

extra() {
    file="$out/$1/$2"
    if [ -f "$file" ]; then
        cat "$file"
    else
        echo "-"
    fi
}

print_table() {
    echo "| | Before | After |"
    echo "|---|---|---|"
    echo "| Home screen | $(extra before home.txt) | $(extra after home.txt) |"
    echo "| Free memory on the TV | $(with_unit "$(value before 2)" " MB") | $(with_unit "$(value after 2)" " MB") |"
    echo "| Home screen memory, on screen | $(with_unit "$(value before 3)" " MB") | $(with_unit "$(value after 3)" " MB") |"
    echo "| Home screen memory, behind another app | $(with_unit "$(value before 4)" " MB") | $(with_unit "$(value after 4)" " MB") |"
    echo "| Google TV's launcher in memory | $(with_unit "$(value before 5)" " MB") | $(with_unit "$(value after 5)" " MB") |"
    echo "| Processes running | $(with_unit "$(value before 6)" "") | $(with_unit "$(value after 6)" "") |"
    echo "| Idle CPU, home screen (% of one core) | $(with_unit "$(value before 7)" "%") | $(with_unit "$(value after 7)" "%") |"
    echo "| Idle CPU, whole TV | $(with_unit "$(value before 8)" "%") | $(with_unit "$(value after 8)" "%") |"
    echo "| Home, app start (am start -W) | $(with_unit "$(value before 9)" " ms") | $(with_unit "$(value after 9)" " ms") |"
    echo "| Home key to the home screen | $(extra before home-key.txt) | $(extra after home-key.txt) |"
    echo "| Frames while moving: drawn, slower than 60/s, typical | $(extra before frames.txt) | $(extra after frames.txt) |"
}

if [ "$state" = table ]; then
    print_table
    exit 0
fi

TV=${TV:-$("$root/scripts/tv/connect.sh")}
export TV
. "$root/scripts/install/accessibility.sh"
remember_accessibility

shell() {
    adb -s "$TV" shell "$@" < /dev/null | tr -d '\r'
}

# Glass Home when it is installed, enabled and catches Home; otherwise the TV's Home app.
home_package=""
home_activity=""
if shell pm list packages -e | grep -qx "package:$GLASS_HOME"; then
    case "${ACCESSIBILITY_SERVICES:-}" in
        *"$GLASS_HOME"*)
            home_package=$GLASS_HOME
            home_activity="$GLASS_HOME/.MainActivity"
            ;;
    esac
fi
if [ -z "$home_package" ]; then
    home_activity=$(shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)
    home_package=${home_activity%%/*}
fi

mkdir -p "$out/$state"
echo "$home_package" > "$out/$state/home.txt"
echo "Measuring '$state': home screen $home_activity, $runs runs, about 2 minutes each"

package_pids() {
    shell ps -A -o PID,NAME | awk -v name="$1" '$2 == name || index($2, name ":") == 1 { print $1 }'
}

package_mb() {
    total_kb=0
    for pid in $(package_pids "$1"); do
        kb=$(shell dumpsys meminfo "$pid" | sed -n 's/.*TOTAL PSS: *\([0-9]*\).*/\1/p' | head -1)
        if [ -n "$kb" ]; then
            total_kb=$((total_kb + kb))
        fi
    done
    echo $((total_kb / 1024))
}

package_ticks() {
    ticks=0
    for pid in $(package_pids "$1"); do
        one=$(shell cat "/proc/$pid/stat" 2>/dev/null | awk '{ print $14 + $15 }')
        if [ -n "$one" ]; then
            ticks=$((ticks + one))
        fi
    done
    echo "$ticks"
}

tv_ticks() {
    shell head -1 /proc/stat | awk '{ idle = $5 + $6; total = 0; for (i = 2; i <= NF; i++) total += $i; print total - idle, total }'
}

printf "run\tfree_mb\thome_mb\thome_behind_mb\tgoogle_tv_mb\tprocesses\tcpu_home\tcpu_tv\thome_ms\n" > "$out/$state/runs.tsv"
for run in $(seq 1 "$runs"); do
    shell input keyevent KEYCODE_WAKEUP
    shell am start -n "$home_activity" > /dev/null 2>&1
    sleep 20
    free=$(shell cat /proc/meminfo | sed -n 's/MemAvailable: *\([0-9]*\).*/\1/p')
    free=$((free / 1024))
    home_mb=$(package_mb "$home_package")
    google_tv_mb=$(package_mb "$GOOGLE_TV")
    processes=$(shell ps -A | tail -n +2 | wc -l | tr -d ' ')

    home_first=$(package_ticks "$home_package")
    tv_first=$(tv_ticks)
    sleep 30
    home_second=$(package_ticks "$home_package")
    tv_second=$(tv_ticks)
    cpu_home=$(awk -v used=$((home_second - home_first)) 'BEGIN { printf "%.1f", used / 100 / 30 * 100 }')
    cpu_tv=$(echo "$tv_first $tv_second" | awk '{ printf "%.1f", ($3 - $1) / ($4 - $2) * 100 }')

    shell am start -W -n "$OTHER_APP" > /dev/null 2>&1
    sleep 15
    behind_mb=$(package_mb "$home_package")
    home_ms=$(shell am start -W -n "$home_activity" | sed -n 's/.*TotalTime: *\([0-9]*\).*/\1/p')
    sleep 5

    printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" "$run" "$free" "$home_mb" "$behind_mb" "$google_tv_mb" "$processes" "$cpu_home" "$cpu_tv" "${home_ms:--}" >> "$out/$state/runs.tsv"
    echo "  run $run: $(tail -1 "$out/$state/runs.tsv")"
done

echo "Frames while moving with the arrow keys"
shell am start -W -n "$home_activity" > /dev/null 2>&1
sleep 8
shell dumpsys gfxinfo "$home_package" reset > /dev/null
for step in 1 2 3 4 5; do
    shell input keyevent KEYCODE_DPAD_DOWN KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_UP
done
sleep 2
info=$(shell dumpsys gfxinfo "$home_package")
total=$(echo "$info" | sed -n 's/.*Total frames rendered: *\([0-9]*\).*/\1/p' | head -1)
slow=$(echo "$info" | sed -n 's/.*Janky frames (legacy): *\([0-9]*\).*/\1/p' | head -1)
typical=$(echo "$info" | sed -n 's/^50th percentile: *\([0-9]*\)ms.*/\1/p' | head -1)
echo "${total:--} drawn, ${slow:--} slower than 60/s, typical ${typical:--} ms" > "$out/$state/frames.txt"
cat "$out/$state/frames.txt"

if [ "$home_package" = "$GLASS_HOME" ]; then
    echo "Home key to Glass Home"
    "$root/scripts/measure/home-key.sh" 5 | tee "$out/$state/home-key-runs.txt" | tail -1
    sed -n 's/^Median: \(.*\)/\1, Google TV not started/p' "$out/$state/home-key-runs.txt" > "$out/$state/home-key.txt"
else
    echo "not measured (needs Glass Home's timing log)" > "$out/$state/home-key.txt"
fi
put_accessibility_back

echo
print_table | tee "$out/table.md"
echo
echo "Saved to $out/table.md"
