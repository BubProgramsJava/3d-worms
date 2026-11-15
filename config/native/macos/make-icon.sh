#!/usr/bin/env bash
# Regenerates worm-icon.icns from the application icon used by the JavaFX stage.
# Run this after changing src/main/resources/se/hirt/bubb/worm/worm-icon.png.
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd -- "$script_dir/../../.." && pwd)
source_png="$repo_root/src/main/resources/se/hirt/bubb/worm/worm-icon.png"
output_icns="$script_dir/worm-icon.icns"

test -f "$source_png"
iconset=$(mktemp -d)/worm.iconset
mkdir -p "$iconset"
trap 'rm -rf "$(dirname "$iconset")"' EXIT

# iconutil refuses an incomplete set, so every standard size is rendered.
while read -r size name; do
    sips -z "$size" "$size" "$source_png" --out "$iconset/$name.png" >/dev/null
done <<'SIZES'
16 icon_16x16
32 icon_16x16@2x
32 icon_32x32
64 icon_32x32@2x
128 icon_128x128
256 icon_128x128@2x
256 icon_256x256
512 icon_256x256@2x
512 icon_512x512
1024 icon_512x512@2x
SIZES

iconutil --convert icns --output "$output_icns" "$iconset"
echo "Wrote $output_icns"
