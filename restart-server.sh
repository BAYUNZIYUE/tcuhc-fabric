#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$DIR/server.pid"
OLD_PID="${1:-}"
LOG_FILE="$DIR/regen-restart.log"

printf '[%s] regen restart helper started (old pid=%s)\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$OLD_PID" >> "$LOG_FILE"

if [[ -n "$OLD_PID" ]]; then
  while kill -0 "$OLD_PID" 2>/dev/null; do
    state="$(ps -p "$OLD_PID" -o stat= 2>/dev/null | tr -d '[:space:]')"
    if [[ -z "$state" || "$state" == Z* ]]; then
      break
    fi
    sleep 1
  done
else
  sleep 2
fi

rm -f "$PID_FILE"
exec "$DIR/start-server.sh" >> "$LOG_FILE" 2>&1
