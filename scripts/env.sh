#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="$ROOT_DIR/.tools/jdk"
export ANDROID_HOME="$ROOT_DIR/.tools/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

if [[ "$(uname -m)" == "aarch64" && -x "$ROOT_DIR/.tools/aapt2" ]]; then
    export BOATSPARK_AAPT2_OVERRIDE="$ROOT_DIR/.tools/aapt2"
fi
