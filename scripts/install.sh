#!/usr/bin/env bash
set -e

echo "===================================================="
echo "  NEXUS AI — Instalasi Local Runtime (Termux/Linux) "
echo "===================================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENGINE_DIR="$PROJECT_ROOT/engine"

if command -v pkg >/dev/null 2>&1; then
    echo "[1/4] Mendeteksi lingkungan Termux Android. Memasang paket sistem..."
    pkg update -y
    pkg install -y python python-pip python-cryptography sqlite git termux-api || pkg install -y python sqlite git
else
    echo "[1/4] Menggunakan lingkungan Python standar ($(python3 --version))..."
fi

echo "[2/4] Memasang dependensi Python Engine (tanpa upgrade pip agar aman di Termux)..."
pip install --no-input -r "$ENGINE_DIR/requirements.txt"

ENV_FILE="$PROJECT_ROOT/.env"
if [ ! -f "$ENV_FILE" ]; then
    RANDOM_TOKEN="nexus-$(python3 -c 'import secrets; print(secrets.token_hex(16))')"
    cat > "$ENV_FILE" <<EOF
NEXUS_HOST=127.0.0.1
NEXUS_PORT=8765
NEXUS_API_TOKEN=$RANDOM_TOKEN
EOF
    chmod 600 "$ENV_FILE"
    echo "[3/4] File .env dibuat dengan token lokal acak."
else
    echo "[3/4] Menggunakan file .env yang sudah ada."
fi

echo "[4/4] Menginisialisasi SQLite (14 tabel + FTS5) & memverifikasi modul..."
set -a
source "$ENV_FILE"
set +a
PYTHONPATH="$ENGINE_DIR" python3 -c "from nexus.main import create_app; create_app(); print('Database & Skills berhasil diinisialisasi!')"

echo ""
echo "===================================================="
echo "INSTALASI SELESAI!"
echo "Endpoint Lokal : http://$NEXUS_HOST:$NEXUS_PORT"
echo "Auth Token     : $NEXUS_API_TOKEN"
echo "Salin Auth Token di atas ke halaman Settings aplikasi Android NEXUS AI."
echo "Jalankan 'bash scripts/start.sh' untuk menyalakan Engine."
echo "===================================================="
