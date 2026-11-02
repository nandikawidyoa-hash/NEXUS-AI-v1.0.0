#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENGINE_DIR="$PROJECT_ROOT/engine"
PID_FILE="$PROJECT_ROOT/.nexus_engine.pid"
LOG_FILE="$PROJECT_ROOT/nexus_engine.log"

if [ -f "$PROJECT_ROOT/.env" ]; then
    set -a
    source "$PROJECT_ROOT/.env"
    set +a
fi

export NEXUS_HOST="${NEXUS_HOST:-127.0.0.1}"
export NEXUS_PORT="${NEXUS_PORT:-8765}"
export NEXUS_API_TOKEN="${NEXUS_API_TOKEN:-nexus-local-dev-token-2026}"

if [ -f "$PID_FILE" ]; then
    OLD_PID="$(cat "$PID_FILE")"
    if kill -0 "$OLD_PID" 2>/dev/null; then
        if python3 -c "import urllib.request; req=urllib.request.Request('http://$NEXUS_HOST:$NEXUS_PORT/health', headers={'Authorization':'Bearer $NEXUS_API_TOKEN'}); urllib.request.urlopen(req, timeout=2)" >/dev/null 2>&1; then
            echo "NEXUS AI Engine sudah berjalan dan merespons (PID: $OLD_PID) di http://$NEXUS_HOST:$NEXUS_PORT"
            exit 0
        else
            kill "$OLD_PID" 2>/dev/null || true
        fi
    fi
    rm -f "$PID_FILE"
fi

if command -v termux-wake-lock >/dev/null 2>&1; then
    termux-wake-lock || true
    echo "[Termux] WakeLock diaktifkan untuk mencegah penghentian proses saat layar redup."
fi

cd "$ENGINE_DIR"
PYTHONPATH="$ENGINE_DIR" nohup python3 -m uvicorn nexus.main:app --host "$NEXUS_HOST" --port "$NEXUS_PORT" > "$LOG_FILE" 2>&1 &
ENGINE_PID=$!
echo "$ENGINE_PID" > "$PID_FILE"

echo "Menunggu NEXUS AI Engine membuka port $NEXUS_PORT..."
READY=0
for i in $(seq 1 15); do
    if ! kill -0 "$ENGINE_PID" 2>/dev/null; then
        break
    fi
    if python3 -c "import urllib.request; req=urllib.request.Request('http://$NEXUS_HOST:$NEXUS_PORT/health', headers={'Authorization':'Bearer $NEXUS_API_TOKEN'}); urllib.request.urlopen(req, timeout=1)" >/dev/null 2>&1; then
        READY=1
        break
    fi
    sleep 1
done

if [ "$READY" -eq 1 ]; then
    echo "NEXUS AI Local Runtime AKTIF & SIAP! (PID: $ENGINE_PID)"
    echo "Alamat     : http://$NEXUS_HOST:$NEXUS_PORT"
    echo "Auth Token : $NEXUS_API_TOKEN"
else
    echo "ERROR: Engine gagal membuka port $NEXUS_PORT. Isi log ($LOG_FILE):"
    cat "$LOG_FILE"
    rm -f "$PID_FILE"
    exit 1
fi
