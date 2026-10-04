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

pack_windows_zip() {  # $1 = source_dir, $2 = output_zip, $3 = base_dir_name
    if command -v python3 >/dev/null 2>&1; then
        python3 - "$1" "$2" "$3" <<'EOF'
import sys, os, zipfile, time

source_dir, output_zip, base_dir = sys.argv[1], sys.argv[2], sys.argv[3]
with zipfile.ZipFile(output_zip, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
    root_zinfo = zipfile.ZipInfo(base_dir + '/', (2026, 1, 1, 0, 0, 0))
    root_zinfo.create_system = 0  # MS-DOS / Windows FAT
    root_zinfo.external_attr = 0x10  # FILE_ATTRIBUTE_DIRECTORY
    zf.writestr(root_zinfo, '')

    for root, dirs, files in os.walk(source_dir):
        rel_dir = os.path.relpath(root, source_dir)
        if rel_dir == '.':
            prefix = base_dir
        else:
            prefix = base_dir + '/' + rel_dir.replace('\\', '/')

        for d in sorted(dirs):
            dir_path = prefix + '/' + d + '/'
            zinfo = zipfile.ZipInfo(dir_path, (2026, 1, 1, 0, 0, 0))
            zinfo.create_system = 0
            zinfo.external_attr = 0x10
            zf.writestr(zinfo, '')

        for f in sorted(files):
            file_full = os.path.join(root, f)
            file_path = prefix + '/' + f
            mtime = time.localtime(os.path.getmtime(file_full))
            dt = (mtime.tm_year, mtime.tm_mon, mtime.tm_mday, mtime.tm_hour, mtime.tm_min, mtime.tm_sec)
            zinfo = zipfile.ZipInfo(file_path, dt[:6])
            zinfo.create_system = 0
            zinfo.external_attr = 0x20  # FILE_ATTRIBUTE_ARCHIVE
            with open(file_full, 'rb') as src:
                zf.writestr(zinfo, src.read(), compress_type=zipfile.ZIP_DEFLATED)
EOF
    else
        (cd "$(dirname "$1")" && zip -qr -X "$2" "$3")
    fi
}

cd "$PROJECT_DIR"
mvn -q clean package
rm -rf "$DIST_DIR"

windows="$DIST_DIR/windows/document-generator"
mkdir -p "$windows"
cp target/document-generator.exe "$windows/"
cp target/document-generator.jar "$windows/"
cp scripts/uruchom.bat "$windows/"
copy_app_files "$windows"
unpack_jre "$(download_jre windows zip)" "$windows"
chmod -R a+rX,u+w,go-w "$windows"
pack_windows_zip "$windows" "$DIST_DIR/document-generator-windows.zip" document-generator

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
