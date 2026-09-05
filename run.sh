#!/bin/sh
cd "$(dirname "$0")"
mkdir -p out
echo "Bien dich / compiling..."
javac -encoding UTF-8 -cp lib/gamelogic.jar -d out src/*.java || {
  echo; echo "!! LOI BIEN DICH -- doc dong 'GameServer.java:<so dong>' o tren, sua, roi chay lai."; exit 1; }
echo "Chay / starting. Ctrl-C de dung."
echo ""
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dbeast.threshold=3 -cp out:lib/gamelogic.jar GameServer "${1:-9000}"
