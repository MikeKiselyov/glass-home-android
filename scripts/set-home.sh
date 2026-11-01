#!/bin/sh
# Makes the Home button lead to Glass Home.
#
# Google TV (and some Android TV builds) keep their own launcher on Home no
# matter which app you choose, and disabling that launcher or its setup wizard
# is risky: on a test TV, disabling the setup wizard (setupwraith) cut the TV
# off the network and it forgot its Wi-Fi. So this script never disables
# them. Instead it turns on Glass Home's small accessibility service, which
# catches the Home key from the remote and opens Glass Home, so the stock home
# screen does not even start (it still opens once when the TV boots, and Glass
# Home comes in front of it). The website's More tab can switch to the older
# way: Google TV first, then Glass Home in front.
#
# On TVs without a stock launcher of that kind it simply sets the default Home app.
#
# Usage: scripts/set-home.sh
set -eu

TV=${TV:-$("$(dirname "$0")/tv-connect.sh")}
. "$(dirname "$0")/changelog.sh"
require_backup

GLASS_HOME=com.glasshome.tv/.MainActivity
OVERRIDE=com.glasshome.tv/com.glasshome.tv.HomeOverrideService
STOCK_LAUNCHERS="com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.leanbacklauncher"

home() {
    tv_shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1
}

enabled=$(tv_shell pm list packages -e | sed 's/^package://')
stock=""
for launcher in $STOCK_LAUNCHERS; do
    if printf "%s\n" "$enabled" | grep -qx "$launcher"; then
        stock="$launcher"
    fi
done

previous=$(home)
tv_shell cmd package set-home-activity "$GLASS_HOME" >/dev/null
echo "home-set $GLASS_HOME $previous" >> "$CHANGES"

if [ -z "$stock" ]; then
    echo "Home button opens: $(home)"
    exit 0
fi

echo "Found the stock launcher $stock; keeping it and turning on the Home override."
services=$(tv_shell settings get secure enabled_accessibility_services)
case "$services" in
    *"$OVERRIDE"*)
        echo "  already on"
        ;;
    ""|null)
        put_setting secure enabled_accessibility_services "$OVERRIDE"
        ;;
    *)
        put_setting secure enabled_accessibility_services "$services:$OVERRIDE"
        ;;
esac
put_setting secure accessibility_enabled 1
echo "Done: Home opens Glass Home."
echo "Force-stopping Glass Home in Settings switches this off (Android does that); run this script again then."
