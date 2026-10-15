#!/usr/bin/env bash
# Push the built Android APK directly to GitHub Release
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

DEFAULT_APK="$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk"

# If first arg doesn't start with - and is an existing file, use it as APK path
if [ $# -ge 1 ] && [[ "$1" != -* ]] && [ -f "$1" ]; then
    APK_PATH="$1"
    shift
    python3 "$SCRIPT_DIR/push_to_github_release.py" --apk "$APK_PATH" "$@"
else
    if [ ! -f "$DEFAULT_APK" ]; then
        echo "Default APK not found at $DEFAULT_APK. Building APK via gradle..."
        gradle assembleDebug
    fi
    python3 "$SCRIPT_DIR/push_to_github_release.py" --apk "$DEFAULT_APK" "$@"
fi
