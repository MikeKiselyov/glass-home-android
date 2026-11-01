#!/bin/sh
# Shared helpers, sourced by scripts that force-stop Glass Home. Android switches
# an app's accessibility services off when the app is force-stopped, which takes
# the Home button away from Glass Home. Remember the setting first, put it back
# after each force-stop. Needs $TV.

remember_accessibility() {
    ACCESSIBILITY_SERVICES=$(adb -s "$TV" shell settings get secure enabled_accessibility_services < /dev/null | tr -d '\r')
}

# Puts the remembered services back if Glass Home's was among them.
put_accessibility_back() {
    case "${ACCESSIBILITY_SERVICES:-}" in
        *com.glasshome.tv*)
            adb -s "$TV" shell settings put secure enabled_accessibility_services "$ACCESSIBILITY_SERVICES" < /dev/null
            adb -s "$TV" shell settings put secure accessibility_enabled 1 < /dev/null
            ;;
    esac
}
