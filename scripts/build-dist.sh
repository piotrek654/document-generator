#!/usr/bin/env bash
# Builds ready-to-copy packages with a bundled Java runtime (Temurin JRE):
#   target/dist/formularz-windows.zip  -> formularz.exe, jre/, config/, data/
#   target/dist/formularz-linux.zip    -> formularz.jar, uruchom.sh, jre/, config/, data/
# The only thing the user still has to install is LibreOffice.
set -euo pipefail

JAVA_FEATURE=25
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/formularz-jre"
DIST_DIR="$PROJECT_DIR/target/dist"

download_jre() {  # $1 = os (windows|linux), $2 = archive extension
    local archive="$CACHE_DIR/jre-$JAVA_FEATURE-$1-x64.$2"
    if [[ ! -f "$archive" ]]; then
        mkdir -p "$CACHE_DIR"
        echo "Downloading JRE $JAVA_FEATURE for $1..." >&2
        curl -fsSL -o "$archive.part" \
            "https://api.adoptium.net/v3/binary/latest/$JAVA_FEATURE/ga/$1/x64/jre/hotspot/normal/eclipse"
        mv "$archive.part" "$archive"
    fi
    echo "$archive"
}

unpack_jre() {  # $1 = archive, $2 = target app directory; the archive has a single top-level folder
    local tmp
    tmp="$(mktemp -d)"
    case "$1" in
        *.zip) unzip -q "$1" -d "$tmp" ;;
        *) tar -xzf "$1" -C "$tmp" ;;
    esac
    mv "$tmp"/*/ "$2/jre"
    rmdir "$tmp"
}

copy_app_files() {  # $1 = target app directory
    mkdir -p "$1/data"
    cp -r "$PROJECT_DIR/config" "$1/"
    find "$PROJECT_DIR/data" -maxdepth 1 -name '*.docx' ! -name '.~lock*' -exec cp {} "$1/data/" \;
}

cd "$PROJECT_DIR"
mvn -q clean package
rm -rf "$DIST_DIR"

windows="$DIST_DIR/windows/formularz"
mkdir -p "$windows"
cp target/formularz.exe "$windows/"
copy_app_files "$windows"
unpack_jre "$(download_jre windows zip)" "$windows"
(cd "$DIST_DIR/windows" && zip -qr "$DIST_DIR/formularz-windows.zip" formularz)

linux="$DIST_DIR/linux/formularz"
mkdir -p "$linux"
cp target/formularz.jar "$linux/"
cp scripts/uruchom.sh "$linux/"
chmod +x "$linux/uruchom.sh"
copy_app_files "$linux"
unpack_jre "$(download_jre linux tar.gz)" "$linux"
(cd "$DIST_DIR/linux" && zip -qr "$DIST_DIR/formularz-linux.zip" formularz)

ls -lh "$DIST_DIR"/*.zip
