#!/usr/bin/env bash
# Crop an image to 1920x1080, save it as the cleanframe theme wallpaper, and push it to the TV.
#
# Usage: TV_HOST=192.168.1.50 tools/set_wallpaper.sh ~/Pictures/valley.jpg
#
# The repo ships no wallpapers: use a photo you own or one you have the right to use.
# Needs macOS `sips` for the crop; on Linux, pre-crop the image and pass it anyway.
set -euo pipefail
DIR="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${1:?usage: TV_HOST=<tv-ip> tools/set_wallpaper.sh <path-to-image>}"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0 <path-to-image>}"

if [ ! -f "$SRC" ]; then
  echo "Error: $SRC does not exist." >&2
  exit 1
fi

mkdir -p "$DIR/themes/cleanframe"
OUT="$DIR/themes/cleanframe/wallpaper.jpg"

if command -v sips >/dev/null 2>&1; then
  echo "Optimizing $(basename "$SRC") for TV..."
  sips --cropToHeightWidth 1080 1920 -s formatOptions 55 "$SRC" --out "$OUT" >/dev/null
else
  echo "sips not found (macOS only). Copying $(basename "$SRC") as-is; make sure it is 1920x1080." >&2
  cp "$SRC" "$OUT"
fi

echo "Applying wallpaper to TV ($TV_HOST)..."
TV_HOST="$TV_HOST" "$DIR/tools/theme.sh" "$DIR/themes/cleanframe"
echo "Done! $(basename "$SRC") is now the wallpaper on $TV_HOST."
