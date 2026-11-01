#!/bin/sh
# How long Home takes: presses Home the way a remote does, with TV Settings in
# front, and prints the time from the key to Glass Home's first frame (from
# Glass Home's own GlassHomeTiming log lines), and whether Google TV's home
# screen was started on the way. Then the median.
#
# The key comes from a virtual input device (scripts/home-key.json, played by
# the TV's uinput tool): "adb shell input keyevent" would skip the accessibility
# key filter that catches Home, so it would measure something else.
#
# Usage: scripts/measure-home.sh [runs]   (default 10)
set -eu

root="$(cd "$(dirname "$0")/.." && pwd)"
TV=${TV:-$("$root/scripts/tv-connect.sh")}
runs=${1:-10}
OTHER_APP=com.android.tv.settings/.MainSettings

shell() {
    adb -s "$TV" shell "$@" | tr -d '\r'
}

adb -s "$TV" push "$root/scripts/home-key.json" /data/local/tmp/home-key.json > /dev/null
results=""
printf "run\thome_press_ms\tgoogle_tv_started\n"
for run in $(seq 1 "$runs"); do
    shell input keyevent KEYCODE_WAKEUP
    shell am start -W -n "$OTHER_APP" > /dev/null 2>&1
    sleep 4
    shell logcat -c
    shell uinput /data/local/tmp/home-key.json > /dev/null 2>&1
    sleep 3
    log=$(shell logcat -d -s GlassHomeTiming:I ActivityTaskManager:I)
    ms=$(printf "%s\n" "$log" | awk '
        /GlassHomeTiming: home key / { key = $NF }
        /GlassHomeTiming: home shown / { if (key != "" && shown == "" && $NF >= key) shown = $NF }
        END { if (key == "" || shown == "") print "-"; else print shown - key }')
    # Google TV's home activity being started is logged by the activity manager.
    stock=no
    if printf "%s\n" "$log" | grep -q "launcherx/.home.HomeActivity"; then
        stock=yes
    fi
    printf "%s\t%s\t%s\n" "$run" "$ms" "$stock"
    results="$results $ms"
done
median=$(printf "%s\n" $results | grep -v -- - | sort -n | awk '{ values[NR] = $1 } END { if (NR == 0) print "-"; else print values[int((NR + 1) / 2)] }')
echo "Median: $median ms"
