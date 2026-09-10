#!/bin/sh
cd "$(dirname "$0")"
PORT="${1:-9000}"
PIDS=$(lsof -ti "tcp:$PORT" 2>/dev/null)
[ -z "$PIDS" ] && PIDS=$(fuser "$PORT/tcp" 2>/dev/null)
[ -z "$PIDS" ] && PIDS=$(ps ax -o pid=,command= 2>/dev/null | grep "[G]ameServer $PORT" | awk '{print $1}')
if [ -z "$PIDS" ]; then
  echo "Nothing to stop -- no server is using port $PORT."
  exit 0
fi
for P in $PIDS; do
  echo "Stopping PID $P on port $PORT"
  kill "$P" 2>/dev/null
done
sleep 1
for P in $PIDS; do
  kill -0 "$P" 2>/dev/null && kill -9 "$P" 2>/dev/null
done
echo "Server on port $PORT stopped."
