#!/bin/sh
# Pairs this computer with the TV once (Android 11+ wireless debugging).
#
# On the TV: Settings > System > Developer options > Wireless debugging >
# Pair device with pairing code. It shows a 6-digit code and an address:port.
#
# Usage: scripts/tv/pair.sh <address:port shown under the code> <code>
set -eu

root="$(cd "$(dirname "$0")/../.." && pwd)"

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <address:port> <pairing code>" >&2
    exit 2
fi
adb pair "$1" "$2"
host=${1%:*}
echo "Paired. Connecting…"
TV_HOST="$host" "$root/scripts/tv/connect.sh"
