#!/usr/bin/env bash
# Builds ready-to-copy packages with a bundled Java runtime (Temurin JRE):
#   target/dist/document-generator-windows.zip  -> document-generator.exe, jre/, config/, data/
#   target/dist/document-generator-linux.zip    -> document-generator.jar, uruchom.sh, jre/, config/, data/
# The only thing the user still has to install is LibreOffice.
set -euo pipefail

JAVA_FEATURE=25
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/document-generator-jre"
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

windows="$DIST_DIR/windows/document-generator"
mkdir -p "$windows"
cp target/document-generator.exe "$windows/"
copy_app_files "$windows"
unpack_jre "$(download_jre windows zip)" "$windows"
chmod -R a+rX,u+w,go-w "$windows"
(cd "$DIST_DIR/windows" && zip -qr -X "$DIST_DIR/document-generator-windows.zip" document-generator)

linux="$DIST_DIR/linux/document-generator"
mkdir -p "$linux"
cp target/document-generator.jar "$linux/"
cp scripts/uruchom.sh "$linux/"
copy_app_files "$linux"
unpack_jre "$(download_jre linux tar.gz)" "$linux"
chmod -R a+rX,u+w,go-w "$linux"
chmod +x "$linux/uruchom.sh"
(cd "$DIST_DIR/linux" && zip -qr "$DIST_DIR/document-generator-linux.zip" document-generator)

ls -lh "$DIST_DIR"/*.zip
