#!/bin/sh
# Makes the system feel quicker: window, transition and animator scales to 0.5x.
#
# Usage: scripts/speed.sh [scale]   (default 0.5; 1 is the stock speed)
set -eu

TV=${TV:-$("$(dirname "$0")/tv-connect.sh")}
. "$(dirname "$0")/changelog.sh"
require_backup

scale=${1:-0.5}
for key in window_animation_scale transition_animation_scale animator_duration_scale; do
    put_setting global "$key" "$scale"
done
