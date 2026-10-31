#!/bin/sh
# Builds the debug APK, installs it on the TV and checks it with a screenshot.
# Installing restarts Glass Home; if it was on screen it is opened again, so
# the TV ends up showing what it showed before. Nothing else on the TV changes.
#
# Usage: scripts/deploy.sh [--no-build]
set -eu

root="$(cd "$(dirname "$0")/.." && pwd)"
PACKAGE=com.glasshome.tv
build=true
for argument in "$@"; do
    case "$argument" in
        --no-build) build=false ;;
        *) echo "unknown option: $argument" >&2; exit 2 ;;
    esac
done

if [ "$build" = true ]; then
    export JAVA_HOME=${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-19.jdk/Contents/Home}
    (cd "$root" && ./gradlew -q assembleDebug)
fi

TV=$("$root/scripts/tv-connect.sh")
front=$(adb -s "$TV" shell dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity || true)
was_in_front=false
case "$front" in *"$PACKAGE/"*) was_in_front=true ;; esac

adb -s "$TV" install -r "$root/app/build/outputs/apk/debug/app-debug.apk" | tail -1
if ! grep -q "^install $PACKAGE\$" "$root/.tv/changes.log" 2>/dev/null; then
    echo "install $PACKAGE" >> "$root/.tv/changes.log"
fi

if [ "$was_in_front" = true ]; then
    adb -s "$TV" shell am start -n "$PACKAGE/.MainActivity" >/dev/null 2>&1
    sleep 5
    TV="$TV" "$root/scripts/tv-shot.sh" "deploy-$(date +%H%M%S)"
else
    echo "Glass Home was not on screen ($front); left the TV as it was."
fi
