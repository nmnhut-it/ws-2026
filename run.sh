#!/bin/sh
cd "$(dirname "$0")"
if ! command -v javac >/dev/null 2>&1; then
  echo "!! NO JDK -- this machine has no javac command."
  echo "   Install JDK 17 or newer: https://adoptium.net  (Latest LTS, default options)"
  echo "   After installing, REOPEN this window and run again."
  exit 1
fi
JV=$(javac -version 2>&1 | sed 's/^javac //')
case "$JV" in [1-9].*|1[0-6].*)
  echo "!! JDK too old (javac $JV) -- 17 or newer needed: https://adoptium.net"
  exit 1;;
esac
mkdir -p out
echo "Compiling..."
javac -encoding UTF-8 -cp "lib/*" -d out src/*.java || {
  echo; echo "!! COMPILE ERROR -- read the 'GameServer.java:<line>' line above, fix it, run again."; exit 1; }
echo "Starting. Ctrl-C to stop."
echo ""
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dbeast.threshold=40 -cp "out:lib/*" GameServer "${1:-9000}"
