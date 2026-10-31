# NEXUS AI — Personal Hybrid Local-Cloud Android AI Agent

NEXUS AI adalah personal AI agent untuk perangkat Android yang menggabungkan **Aplikasi Kontrol Android (Kotlin + Jetpack Compose)** dengan **Hybrid Python Agent Engine (FastAPI + SQLite FTS5 + WebSocket)** yang berjalan secara lokal di **Termux** maupun tersinkronisasi dengan **Cloud Runtime** opsional.

## Fitur Utama
- **8-Step Agent Workflow**: Memproses setiap perintah melalui tahapan *Understand → Plan → Select Runtime → Select Model → Execute → Verify → Remember → Report*.
- **Hybrid Runtime (Local-First + Cloud Failover)**: Berjalan 100% lokal di HP melalui Termux (`http://127.0.0.1:8765`) tanpa mewajibkan VPS berbayar, serta mendukung sinkronisasi ke Cloud Runtime.
- **Multi-Provider AI & Auto Routing**: Mendukung Google Gemini, Qwen, DeepSeek, OpenAI, dan Custom OpenAI-compatible API dengan enkripsi API key (Fernet AES), masking ketat, dan fallback otomatis.
- **SQLite 14 Tabel + FTS5 Memory**: Memisahkan *short-term*, *long-term*, *project*, *conversation*, dan *task memory* dengan pencarian teks instan FTS5 serta filter anti-kebocoran API key.
- **Built-in Agent Tools**: File Management (dengan pembatasan workspace & auto-backup/rollback), Terminal non-root berbatas izin, Web Research, dan Coding Agent.
- **Modular Skills System (`SKILL.md`)**: Mendukung pemasangan, penonaktifan, dan pemindaian keamanan skill pihak ketiga.
- **Keamanan Semi-Autonomous**: Klasifikasi risiko (*Low, Medium, High, Critical*), tombol darurat **Stop Agent**, **Cancel Task**, dan **Approval Center**.
- **UI Android Minimalis Modern**: 5 halaman utama (*Home, Chat, Tasks, Skills, Settings*) dan 9 halaman kendali pendukung dengan dukungan Dark/Light mode.

## Struktur Direktori
- `android/` — Source code aplikasi Android (Kotlin, Jetpack Compose, Material 3, OkHttp REST + WebSocket, ForegroundService, DeviceBridge).
- `engine/` — Python Hybrid Agent Engine (`nexus/`) beserta automated test suite (`tests/`).
- `skills/` — Skill modular bawaan (`coding`, `research`, `file-management`, `android`).
- `workspace/` — Ruang kerja terisolasi (`projects/`, `documents/`, `downloads/`, `backups/`).
- `scripts/` — Skrip operasional Termux (`install.sh`, `start.sh`, `stop.sh`, `status.sh`).
- `docs/` — Dokumentasi arsitektur, referensi API, keamanan, dan panduan instalasi/build dari HP.
- `.github/workflows/build-android-apk.yml` — Pipeline CI/CD otomatis untuk menghasilkan file APK langsung dari GitHub tanpa PC.

## Memulai Cepat di Termux (HP Android)
```bash
bash scripts/install.sh
bash scripts/start.sh
bash scripts/status.sh
```
Lihat panduan lengkap di [`docs/setup-guide.md`](docs/setup-guide.md).
