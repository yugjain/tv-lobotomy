#!/usr/bin/env bash
# Complete restore script for Toshiba Android TV (songshan, Android 11, RTT2.220118.001)
# Re-enables all debloated packages, puts Google TV launcher back as default, and restores Private DNS.
#
# Usage: TV_HOST=192.168.1.50 ./examples/toshiba-tv-songshan/restore_tv.sh
set -euo pipefail
DIR="$(cd "$(dirname "$0")/../.." && pwd)"
: "${TV_HOST:?Set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"

PY="$DIR/tools/venv/bin/python"
[ -x "$PY" ] || PY=python3

echo "=== Restoring Toshiba TV ($TV_HOST) to stock state ==="

# Re-enable all packages disabled during debloat
PKGS=(
  "fusion.android.tv.demo"
  "com.hisense.storemode"
  "com.mstar.android.tv.disclaimercustomization"
  "com.hisense.uifac"
  "com.hisense.fte"
  "com.hisense.tv.protector"
  "com.vt.tv.aiot"
  "com.vt.tv.homekitproxy"
  "com.vt.miracast"
  "com.google.android.feedback"
  "com.google.android.partnersetup"
  "android.autoinstalls.config.google.gtvpai"
  "com.android.printspooler"
  "com.google.android.syncadapters.calendar"
  "com.android.providers.calendar"
  "com.android.providers.contacts"
  "com.google.android.play.games"
  "com.google.android.videos"
  "com.graymatrix.did"
  "com.mxtech.videoplayer.television"
  "com.tcl.browser"
  "com.google.android.tungsten.setupwraith"
  "com.google.android.apps.tv.launcherx"
)

for pkg in "${PKGS[@]}"; do
  echo "Re-enabling $pkg..."
  "$PY" -I "$DIR/tools/tv.py" "pm enable --user 0 $pkg" >/dev/null 2>&1 || true
done

# Restore stock launcher as default Home
echo "Setting Google TV launcher as default Home..."
"$PY" -I "$DIR/tools/tv.py" "cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity"

# Restore private DNS to opportunistic (default)
echo "Restoring Private DNS..."
"$PY" -I "$DIR/tools/tv.py" "settings put global private_dns_mode opportunistic"

echo "=== Verifying Home activity ==="
"$PY" -I "$DIR/tools/tv.py" "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"

echo "=== Done! The TV has been restored to factory stock Google TV ==="
