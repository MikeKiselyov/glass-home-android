#!/bin/sh
# Builds the APK, installs it on the TV and checks it with a screenshot.
# Installing restarts Glass Home; if it was on screen it is opened again, so
# the TV ends up showing what it showed before. Nothing else on the TV changes.
#
# Usage: scripts/deploy.sh [--release] [--no-build]
#   --release   the build people install: R8, no debugging, compiled ahead of time (speed)
#               on the TV right after installing (otherwise Android does that
#               overnight). Debug builds allow run-as, which some scripts use.
set -eu

root="$(cd "$(dirname "$0")/.." && pwd)"
PACKAGE=com.glasshome.tv
build=true
build_type=debug
for argument in "$@"; do
    case "$argument" in
        --no-build) build=false ;;
        --release) build_type=release ;;
        *) echo "unknown option: $argument" >&2; exit 2 ;;
    esac
done

if [ "$build" = true ]; then
    # The JDK the project was built with on macOS, when JAVA_HOME is not set.
    if [ -z "${JAVA_HOME:-}" ] && [ -d /Library/Java/JavaVirtualMachines/jdk-19.jdk/Contents/Home ]; then
        export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-19.jdk/Contents/Home
    fi
    if [ "$build_type" = release ]; then
        (cd "$root" && ./gradlew -q assembleRelease)
    else
        (cd "$root" && ./gradlew -q assembleDebug)
    fi
fi

TV=$("$root/scripts/tv-connect.sh")
front=$(adb -s "$TV" shell dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity || true)
was_in_front=false
case "$front" in *"$PACKAGE/"*) was_in_front=true ;; esac

installed=$(adb -s "$TV" install -r "$root/app/build/outputs/apk/$build_type/app-$build_type.apk" 2>&1 || true)
case "$installed" in
    *Success*) echo "Installed the $build_type build." ;;
    *)
        echo "$installed" >&2
        echo "Could not install. INSTALL_FAILED_UPDATE_INCOMPATIBLE means a build signed with another key is on the TV: uninstall it first (its settings and photos go with it)." >&2
        exit 1
        ;;
esac
if [ "$build_type" = release ]; then
    adb -s "$TV" shell cmd package compile -m speed -f "$PACKAGE" | tail -1
fi
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
