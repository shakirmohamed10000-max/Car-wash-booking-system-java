#!/usr/bin/env bash
# Compiles everything including the tests, then runs the test suite.
# Tests use temporary folders and never touch the real data/ folder.
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v javac >/dev/null 2>&1; then
    echo "Java JDK 17 or newer is required (javac was not found)."
    exit 1
fi

rm -rf out
mkdir out
javac --release 17 -d out $(find src test -name '*.java')
java -cp out tests.TestRunner
