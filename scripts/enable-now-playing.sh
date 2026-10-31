#!/bin/sh
# Lets Glass Home see what is playing (Spotify, YouTube Music...) to show the
# track and cover art on the home screen. Android only allows this for apps
# with notification access; Glass Home reads media sessions, not notifications.
#
# Usage: scripts/enable-now-playing.sh
set -eu

TV=${TV:-$("$(dirname "$0")/tv-connect.sh")}
. "$(dirname "$0")/changelog.sh"

LISTENER=com.glasshome.tv/com.glasshome.tv.MediaListener
tv_shell cmd notification allow_listener "$LISTENER"
echo "listener-allowed $LISTENER" >> "$CHANGES"
echo "Now playing is on. Undo with scripts/restore.sh"
