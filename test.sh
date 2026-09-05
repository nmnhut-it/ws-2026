#!/bin/sh
cd "$(dirname "$0")"
mkdir -p tests/out
echo "Bien dich test / compiling tests..."
javac -encoding UTF-8 -cp lib/gamelogic.jar -d tests/out src/*.java tests/src/Tests.java || exit 1
echo ""
java -Dstdout.encoding=UTF-8 -cp tests/out:lib/gamelogic.jar Tests
