#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PID_FILE="$PROJECT_ROOT/.nexus_engine.pid"

if [ -f "$PID_FILE" ]; then
    PID="$(cat "$PID_FILE")"
    if kill -0 "$PID" 2>/dev/null; then
        kill "$PID"
        echo "NEXUS AI Engine (PID: $PID) berhasil dihentikan."
    else
        echo "Proses PID $PID sudah tidak aktif."
    fi
    rm -f "$PID_FILE"
else
    echo "File PID tidak ditemukan. Engine sedang tidak berjalan."
fi

if command -v termux-wake-unlock >/dev/null 2>&1; then
    termux-wake-unlock || true
fi
