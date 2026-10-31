#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PID_FILE="$PROJECT_ROOT/.nexus_engine.pid"
LOG_FILE="$PROJECT_ROOT/nexus_engine.log"

if [ -f "$PROJECT_ROOT/.env" ]; then
    set -a
    source "$PROJECT_ROOT/.env"
    set +a
fi

HOST="${NEXUS_HOST:-127.0.0.1}"
PORT="${NEXUS_PORT:-8765}"
TOKEN="${NEXUS_API_TOKEN:-nexus-local-dev-token-2026}"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "Status Proses : ONLINE (PID $(cat "$PID_FILE"))"
else
    echo "Status Proses : OFFLINE"
fi

python3 -c "
import urllib.request
try:
    req = urllib.request.Request(
        'http://$HOST:$PORT/health',
        headers={'Authorization': 'Bearer $TOKEN'}
    )
    with urllib.request.urlopen(req, timeout=3) as resp:
        print('Respons /health :', resp.status, resp.read().decode('utf-8'))
except Exception as e:
    print('Endpoint tidak dapat dihubungi:', e)
    exit(1)
" || {
    if [ -f "$LOG_FILE" ]; then
        echo ""
        echo "=== 20 Baris Terakhir nexus_engine.log ==="
        tail -n 20 "$LOG_FILE"
    fi
}
