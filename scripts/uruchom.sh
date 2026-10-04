#!/usr/bin/env bash
# Starts the form with the bundled JRE (falls back to java from PATH).
# Runs from its own folder, because config/, data/ and output/ are relative paths.
cd "$(dirname "$0")" || exit 1
JAVA=./jre/bin/java
[[ -x "$JAVA" ]] || JAVA=java
exec "$JAVA" -jar document-generator.jar "$@"
