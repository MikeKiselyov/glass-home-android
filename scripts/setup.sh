#!/bin/sh
# Sets up a TV in one go: backup, install, Home button, and the optional extras.
# Asks before each step (Enter takes the answer in [brackets]), or takes the
# answers from options. Every change is recorded in .tv/changes.log, and a
# fresh backup is saved first, so scripts/restore.sh can put everything back.
#
# Three ways to use it:
#   all        Glass Home and the TV clean-up (the default)
#   glasshome  only Glass Home: install, Home button, now playing
#   clean      only the clean-up (ads, tracking, animations, DNS, screensaver);
#              Glass Home is not installed and the TV keeps its own home screen
#
# Usage: scripts/setup.sh [options]
#   --only all|glasshome|clean  which of the three (asked first otherwise)
#   --yes                     no questions: the recommended answers below
#   --apk <file>              install this APK instead of building one
#   --home on|off             Home button opens Glass Home (recommended: on)
#   --now-playing on|off      show what Spotify / YouTube Music play (on)
#   --clean ask|junk|none     switch off ads, tracking and demo apps
#                             (ask: one by one; junk: all junk, no features; --yes: junk)
#   --animations 0.5|1|keep   system animation speed (0.5: twice as fast)
#   --dns adguard|keep        block ads and trackers for every app with Private DNS (keep)
#   --screensaver keep|glasshome|colors|off
#                             Google's photo screensaver uses the network and
#                             memory while the TV sits idle; glasshome shows
#                             Glass Home's photos and clock instead (keep)
#
# Examples:
#   scripts/setup.sh                       asks about everything
#   scripts/setup.sh --yes                 recommended set-up, no questions
#   scripts/setup.sh --yes --clean none    the same, but nothing switched off
#   scripts/setup.sh --only glasshome --apk glass-home.apk
#                                          only Glass Home, from a downloaded APK
#   scripts/setup.sh --only clean --yes    only the clean-up, no Glass Home
set -eu

root="$(cd "$(dirname "$0")/.." && pwd)"
scripts="$root/scripts"

yes=false
only=""
apk=""
home=""
now_playing=""
clean=""
animations=""
dns=""
screensaver=""

while [ "$#" -gt 0 ]; do
    case "$1" in
        --yes) yes=true ;;
        --only) shift; only="${1:-}" ;;
        --apk) shift; apk="${1:-}" ;;
        --home) shift; home="${1:-}" ;;
        --now-playing) shift; now_playing="${1:-}" ;;
        --clean) shift; clean="${1:-}" ;;
        --animations) shift; animations="${1:-}" ;;
        --dns) shift; dns="${1:-}" ;;
        --screensaver) shift; screensaver="${1:-}" ;;
        -h|--help) sed -n '/^set -eu$/q;2,$p' "$0"; exit 0 ;;
        *) echo "unknown option: $1 (see scripts/setup.sh --help)" >&2; exit 2 ;;
    esac
    shift
done

# ask <question> <recommended answer> <other answers...>: prints the choice.
ask() {
    question=$1
    recommended=$2
    shift 2
    if [ "$yes" = true ]; then
        echo "$recommended"
        return
    fi
    choices="[$recommended]"
    for other in "$@"; do
        choices="$choices/$other"
    done
    printf "%s %s " "$question" "$choices" >&2
    read -r answer < /dev/tty
    if [ -z "$answer" ]; then
        answer=$recommended
    fi
    echo "$answer"
}

check_choice() {
    name=$1
    value=$2
    shift 2
    for allowed in "$@"; do
        if [ "$value" = "$allowed" ]; then
            return 0
        fi
    done
    echo "--$name must be one of: $*" >&2
    exit 2
}

# Questions need a terminal (not there under "ssh host script" or in CI).
if [ "$yes" = false ] && ! (: < /dev/tty) 2>/dev/null; then
    echo "No terminal to ask questions in: add --yes, or give every answer as an option." >&2
    exit 2
fi

if [ -z "$only" ]; then
    only=$(ask "What to set up: Glass Home and the clean-up, only Glass Home, or only the clean-up?" all glasshome clean)
fi
check_choice only "$only" all glasshome clean
glass_home=true
cleanup=true
if [ "$only" = glasshome ]; then
    cleanup=false
fi
if [ "$only" = clean ]; then
    glass_home=false
    if [ -n "$apk" ] || [ -n "$home" ] || [ -n "$now_playing" ]; then
        echo "--only clean does not install Glass Home: leave out --apk, --home and --now-playing." >&2
        exit 2
    fi
fi
if [ "$only" = glasshome ]; then
    if [ -n "$clean" ] || [ -n "$animations" ] || [ -n "$dns" ] || [ -n "$screensaver" ]; then
        echo "--only glasshome leaves the TV as it is: leave out --clean, --animations, --dns and --screensaver." >&2
        exit 2
    fi
fi

echo "1. Connecting to the TV"
TV=$("$scripts/tv/connect.sh")
export TV
echo "   $TV: $(adb -s "$TV" shell getprop ro.product.model | tr -d '\r'), Android $(adb -s "$TV" shell getprop ro.build.version.release | tr -d '\r')"

echo "2. Backing up the TV as it is now"
"$scripts/backup/backup.sh"

if [ "$glass_home" = true ]; then
    echo "3. Installing Glass Home"
    if [ -n "$apk" ]; then
        if ! "$scripts/install/install-apk.sh" "$apk"; then
            echo "Nothing else was changed." >&2
            exit 1
        fi
        echo "   installed $apk"
        if ! grep -q "^install com.glasshome.tv\$" "$root/.tv/changes.log" 2>/dev/null; then
            echo "install com.glasshome.tv" >> "$root/.tv/changes.log"
        fi
        adb -s "$TV" shell cmd package compile -m speed -f com.glasshome.tv | tail -1
    else
        "$scripts/install/deploy.sh" --release
    fi

    if [ -z "$home" ]; then
        home=$(ask "4. Make the Home button open Glass Home?" on off)
    fi
    check_choice home "$home" on off
    if [ "$home" = on ]; then
        "$scripts/install/set-home.sh"
    fi

    if [ -z "$now_playing" ]; then
        now_playing=$(ask "5. Show what is playing (Spotify, YouTube Music) on the home screen?" on off)
    fi
    check_choice now-playing "$now_playing" on off
    if [ "$now_playing" = on ]; then
        "$scripts/install/enable-now-playing.sh"
    fi
fi

if [ "$cleanup" = true ]; then
    if [ -z "$clean" ]; then
        clean=$(ask "6. Switch off ads, tracking and demo apps? (ask = one by one)" junk ask none)
    fi
    check_choice clean "$clean" ask junk none
    if [ "$clean" = ask ]; then
        "$scripts/cleanup/debloat.sh"
    fi
    if [ "$clean" = junk ]; then
        "$scripts/cleanup/debloat.sh" --yes-junk
    fi

    if [ -z "$animations" ]; then
        animations=$(ask "7. System animation speed? (0.5 = twice as fast)" 0.5 1 keep)
    fi
    check_choice animations "$animations" 0.5 1 keep
    if [ "$animations" != keep ]; then
        "$scripts/cleanup/speed.sh" "$animations"
    fi

    if [ -z "$dns" ]; then
        dns=$(ask "8. Block ads and trackers for every app with AdGuard's Private DNS?" keep adguard)
    fi
    check_choice dns "$dns" adguard keep
    if [ "$dns" = adguard ]; then
        "$scripts/cleanup/private-dns.sh"
    fi

    if [ -z "$screensaver" ]; then
        screensaver=$(ask "9. Screensaver: keep Google's, Glass Home's photos and clock, plain colours, or off?" keep glasshome colors off)
    fi
    check_choice screensaver "$screensaver" keep glasshome colors off
    . "$scripts/lib/changelog.sh"
    if [ "$screensaver" = glasshome ]; then
        put_setting secure screensaver_components com.glasshome.tv/.GlassHomeDream
        put_setting secure screensaver_enabled 1
    fi
    if [ "$screensaver" = colors ]; then
        put_setting secure screensaver_components com.android.dreams.basic/.Colors
    fi
    if [ "$screensaver" = off ]; then
        put_setting secure screensaver_enabled 0
    fi
fi

echo
echo "Done. The backup from before is in $(cat "$root/.tv/latest-backup")."
echo "Every change is listed in .tv/changes.log. To put the TV back:"
echo "  scripts/restore.sh --dry-run    shows what it would do"
echo "  scripts/restore.sh              does it (Glass Home stays installed)"
echo "  scripts/restore.sh --uninstall  also removes Glass Home"
