#!/usr/bin/env bash
# Build and install the launcher on the Toshiba (songshan) TV.
#
# This is a thin wrapper around launcher/build-and-install.sh, which detects JDK 17 and the
# Android SDK itself. Set JAVA_HOME / ANDROID_HOME if it can't find them.
#
# Usage: TV_HOST=192.168.1.50 ./examples/toshiba-tv-songshan/build_and_install_tv.sh
set -euo pipefail
DIR="$(cd "$(dirname "$0")/../.." && pwd)"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"

echo "Building and installing on TV at $TV_HOST..."
cd "$DIR/launcher"
# INPUT_KEY_SERVICE=1 installs the accessibility service that lets the launcher map remote keys.
INPUT_KEY_SERVICE=1 TV_HOST="$TV_HOST" ./build-and-install.sh
echo "Launcher build and install complete."
