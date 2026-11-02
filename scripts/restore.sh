#!/bin/sh
# Undoes every change recorded in .tv/changes.log, newest first:
#   disable <package>                 -> pm enable <package>
#   setting <namespace> <key> <old>   -> settings put back <old> (or delete if it was <absent>)
#   home-disabled <package>           -> pm enable <package> (the stock launcher)
#   home-set <new> <previous>         -> cmd package set-home-activity <previous>
#   listener-allowed <component>      -> cmd notification disallow_listener <component>
#   install <package>                 -> uninstall only with --uninstall
#
# Usage: scripts/restore.sh [--uninstall] [--dry-run]   (TV=<serial> to pick a device)
set -eu

root="$(cd "$(dirname "$0")/.." && pwd)"
TV=${TV:-$("$root/scripts/tv/connect.sh")}
log="$root/.tv/changes.log"
uninstall=false
dry_run=false

for argument in "$@"; do
    case "$argument" in
        --uninstall) uninstall=true ;;
        --dry-run) dry_run=true ;;
        *) echo "unknown option: $argument" >&2; exit 2 ;;
    esac
done

if [ ! -s "$log" ]; then
    echo "Nothing to restore: $log is empty or missing."
    exit 0
fi

run() {
    echo "+ adb shell $*"
    if [ "$dry_run" = false ]; then
        # </dev/null: adb would otherwise read the rest of the change log from stdin.
        adb -s "$TV" shell "$@" < /dev/null 2>&1 | grep -v "Init wrapper" || true
    fi
}

adb connect "$TV" >/dev/null 2>&1 || true
adb -s "$TV" get-state >/dev/null

if [ "$dry_run" = false ]; then
    # The state before undoing, too: if the undo itself goes wrong, this is what it was.
    TV="$TV" "$root/scripts/backup/backup.sh"
fi

# Newest change first. (awk rather than "tail -r", which Linux doesn't have.)
awk '{ line[NR] = $0 } END { for (number = NR; number > 0; number--) print line[number] }' "$log" | while read -r action first second third; do
    case "$action" in
        disable)
            run pm enable "$first"
            ;;
        home-disabled)
            run pm enable "$first"
            ;;
        home-set)
            run cmd package set-home-activity "$second"
            ;;
        listener-allowed)
            run cmd notification disallow_listener "$first"
            ;;
        setting)
            if [ "$third" = "<absent>" ]; then
                run settings delete "$first" "$second"
            else
                run settings put "$first" "$second" "$third"
            fi
            ;;
        install)
            if [ "$uninstall" = true ]; then
                run pm uninstall "$first"
            else
                echo "  keeping installed $first (pass --uninstall to remove it)"
            fi
            ;;
        *)
            echo "  skipping unknown line: $action $first $second $third"
            ;;
    esac
done

if [ "$dry_run" = false ]; then
    stamp=$(date +%Y%m%d-%H%M%S)
    mv "$log" "$log.restored-$stamp"
    echo "Done. The log was moved to $log.restored-$stamp"
fi
