#!/bin/sh
# Prints the TV's adb serial (host:port), connecting first when needed.
#
# Wireless debugging picks a new port after every reboot; the TV announces the
# current one over mDNS, so this finds it without looking at the TV. A paired
# computer stays paired across reboots (pair once with scripts/tv/pair.sh).
#
# With one TV on the network nothing needs configuring. With several, set
# TV_HOST to the one you mean, e.g. TV_HOST=192.168.1.20.
#
# Usage: TV=$(scripts/tv/connect.sh)
set -eu

TV_HOST=${TV_HOST:-}

# Already connected?
connected=$(adb devices | awk -v host="$TV_HOST" '$2 == "device" && $1 ~ /:[0-9]+$/ && (host == "" || index($1, host ":") == 1) { print $1 }')
connected_count=$(printf "%s" "$connected" | grep -c . || true)
if [ "$connected_count" -eq 1 ]; then
    echo "$connected"
    exit 0
fi

# Otherwise find it over mDNS.
endpoints=$(adb mdns services | awk -v host="$TV_HOST" '$2 == "_adb-tls-connect._tcp" && (host == "" || index($3, host ":") == 1) { print $3 }' | sort -u)
endpoint_count=$(printf "%s" "$endpoints" | grep -c . || true)

if [ "$endpoint_count" -eq 0 ]; then
    echo "No TV with wireless debugging found on the network." >&2
    echo "On the TV: Settings > System > Developer options > Wireless debugging (turn it on)." >&2
    echo "First time on this computer? Pair it: scripts/tv/pair.sh" >&2
    exit 1
fi
if [ "$endpoint_count" -gt 1 ]; then
    echo "Several devices offer wireless debugging. Pick one with TV_HOST=<address>:" >&2
    echo "$endpoints" >&2
    exit 1
fi

adb connect "$endpoints" >/dev/null
adb -s "$endpoints" wait-for-device
echo "$endpoints"
