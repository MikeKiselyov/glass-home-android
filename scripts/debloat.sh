#!/bin/sh
# Disables ads, tracking and unwanted apps from debloat/packages.tsv, asking
# about each one that is installed and enabled on this TV. Nothing is
# uninstalled; scripts/restore.sh turns everything back on.
#
# Usage: scripts/debloat.sh                  asks about each one
#        scripts/debloat.sh --yes-junk       no questions: disables all "junk", keeps every "feature"
#        scripts/debloat.sh --disable a,b    also disables these packages (e.g. features you said you don't use)
set -eu

TV=${TV:-$("$(dirname "$0")/tv-connect.sh")}
. "$(dirname "$0")/changelog.sh"

yes_junk=false
extra=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        --yes-junk) yes_junk=true ;;
        --disable) shift; extra="${1:-}" ;;
        *) echo "unknown option: $1" >&2; exit 2 ;;
    esac
    shift
done

require_backup
enabled=$(tv_shell pm list packages -e | sed 's/^package://')

grep -v '^#' "$root/debloat/packages.tsv" | while IFS="$(printf '\t')" read -r package kind description; do
    if ! printf "%s\n" "$enabled" | grep -qx "$package"; then
        continue
    fi
    if [ "$kind" = "junk" ]; then
        if [ "$yes_junk" = true ]; then
            disable_package "$package"
            continue
        fi
        printf "%s\n  %s. Disable? [Y/n] " "$package" "$description"
        read -r answer < /dev/tty
        case "$answer" in
            n|N|no) echo "  kept" ;;
            *) disable_package "$package" ;;
        esac
    else
        if [ "$yes_junk" = true ]; then
            continue
        fi
        printf "%s\n  %s. Do you use it? Disable? [y/N] " "$package" "$description"
        read -r answer < /dev/tty
        case "$answer" in
            y|Y|yes) disable_package "$package" ;;
            *) echo "  kept" ;;
        esac
    fi
done
for package in $(printf "%s" "$extra" | tr ',' ' '); do
    if printf "%s\n" "$enabled" | grep -qx "$package"; then
        disable_package "$package"
    fi
done
echo "Done. Undo everything with scripts/restore.sh"
