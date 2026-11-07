#!/usr/bin/env bash
# Compiles (when sources changed) and starts the program.
# Always runs from the project root so the data/ folder is the same every time.
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v javac >/dev/null 2>&1; then
    echo "Java JDK 17 or newer is required (javac was not found)."
    exit 1
fi

needs_build=false
if [ ! -d out ]; then
    needs_build=true
elif [ -n "$(find src -name '*.java' -newer out -print -quit)" ]; then
    needs_build=true
fi

if [ "$needs_build" = true ]; then
    rm -rf out
    mkdir out
    javac --release 17 -d out $(find src -name '*.java')
    touch out
fi

exec java -cp out app.Main
