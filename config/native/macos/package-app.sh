#!/usr/bin/env bash
# Wraps the GraalVM Native Image executable in a 3D Worms.app bundle and a disk image.
# Native Image produces a bare executable, so the bundle, icon, and DMG are assembled here
# instead of by GluonFX.
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd -- "$script_dir/../../.." && pwd)

app_name="3D Worms"
executable_name="3d-worms"
executable="$repo_root/target/native/$executable_name"
version="1.0.0"
output_dir="$repo_root/dist"
dmg_name=""
stage_dir="$repo_root/target/macos-bundle"

usage() {
    cat <<'USAGE'
Usage: package-app.sh [options]

  --executable PATH   Native Image executable (default target/native/3d-worms)
  --version VERSION   Bundle version without a leading v (default 1.0.0)
  --output-dir DIR    Where the disk image is written (default dist)
  --dmg-name NAME     Disk image file name (default 3d-worms-<version>-macos-<arch>.dmg)
USAGE
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --executable) executable="$2"; shift 2 ;;
        --version) version="$2"; shift 2 ;;
        --output-dir) output_dir="$2"; shift 2 ;;
        --dmg-name) dmg_name="$2"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
    esac
done

if [[ ! -x "$executable" ]]; then
    echo "Native executable not found or not executable: $executable" >&2
    exit 1
fi

case "$(uname -m)" in
    arm64) arch="aarch64" ;;
    x86_64) arch="x86_64" ;;
    *) arch="$(uname -m)" ;;
esac
if [[ -z "$dmg_name" ]]; then
    dmg_name="$executable_name-$version-macos-$arch.dmg"
fi

bundle="$stage_dir/$app_name.app"
rm -rf "$stage_dir"
mkdir -p "$bundle/Contents/MacOS" "$bundle/Contents/Resources"

cp "$executable" "$bundle/Contents/MacOS/$executable_name"
chmod +x "$bundle/Contents/MacOS/$executable_name"
cp "$script_dir/worm-icon.icns" "$bundle/Contents/Resources/worm-icon.icns"
sed "s/__VERSION__/$version/g" "$script_dir/Info.plist" > "$bundle/Contents/Info.plist"
printf 'APPL????' > "$bundle/Contents/PkgInfo"

# Releases carry no Developer ID. An ad-hoc signature is still required for the bundle to
# launch on Apple Silicon, so re-sign after the executable was copied into the bundle.
codesign --force --sign - --timestamp=none "$bundle"
codesign --verify --deep --strict "$bundle"

mkdir -p "$output_dir"
dmg_path="$output_dir/$dmg_name"
rm -f "$dmg_path"
dmg_root="$stage_dir/dmg"
mkdir -p "$dmg_root"
cp -R "$bundle" "$dmg_root/"
ln -s /Applications "$dmg_root/Applications"
hdiutil create -quiet -volname "$app_name" -srcfolder "$dmg_root" -ov -format UDZO "$dmg_path"

echo "Wrote $dmg_path"
