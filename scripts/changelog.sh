#!/bin/sh
# Shared helpers: every change to the TV is appended to .tv/changes.log so that
# scripts/restore.sh can undo it. Sourced by the other scripts.

root="$(cd "$(dirname "$0")/.." && pwd)"
if [ ! -f "$root/scripts/changelog.sh" ]; then
    # Sourced from somewhere other than a script in scripts/: refuse rather
    # than write the change log to the wrong place.
    echo "changelog.sh must be used by the scripts in scripts/" >&2
    exit 1
fi
CHANGES="$root/.tv/changes.log"
mkdir -p "$root/.tv"

require_backup() {
    if [ ! -s "$root/.tv/latest-backup" ]; then
        echo "No backup yet; making one first."
        TV="$TV" "$root/scripts/backup.sh"
    fi
}

# </dev/null: inside "while read" loops adb would otherwise swallow the loop's input.
tv_shell() {
    adb -s "$TV" shell "$@" < /dev/null 2>&1 | grep -v "Init wrapper" || true
}

# Never disabled, whatever a list or a person asks: the setup wizard (disabling
# it cut a test TV off the network and it forgot its Wi-Fi), launchers, network,
# system UI, settings, Play services, the keyboard and HDMI/tuner inputs.
PROTECTED_PATTERN='^(android|com\.android\.(systemui|settings|tv\.settings|providers\..*|networkstack.*|wifi.*|bluetooth|shell|packageinstaller|permissioncontroller|inputdevices|externalstorage)|com\.google\.android\.(tungsten\.setupwraith|apps\.tv\.launcherx|tvlauncher|leanbacklauncher|gms|gsf|ext\..*|packageinstaller|permissioncontroller|inputmethod\..*|webview|tv\.remote\.service|partnersetup)|com\.android\.vending|com\.glasshome\.tv|com\.mediatek\.(tvinput|wwtv\.tvcenter|tv\.service)|com\.express\.at\.factory\.factorymanager)$'

is_protected() {
    printf "%s\n" "$1" | grep -Eq "$PROTECTED_PATTERN"
}

# disable_package <package>: disables (never uninstalls) and records it.
disable_package() {
    if is_protected "$1"; then
        echo "  refusing to disable $1: the TV needs it"
        return 0
    fi
    result=$(tv_shell pm disable-user --user 0 "$1")
    case "$result" in
        *disabled-user*)
            echo "disable $1" >> "$CHANGES"
            echo "  disabled $1"
            ;;
        *)
            echo "  could not disable $1: $result"
            ;;
    esac
}

# put_setting <namespace> <key> <value>: records the old value, then sets it.
put_setting() {
    old=$(tv_shell settings get "$1" "$2")
    if [ -z "$old" ] || [ "$old" = "null" ]; then
        old="<absent>"
    fi
    tv_shell settings put "$1" "$2" "$3" >/dev/null
    echo "setting $1 $2 $old" >> "$CHANGES"
    echo "  $1 $2: $old -> $3"
}
