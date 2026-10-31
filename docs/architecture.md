# Arsitektur NEXUS AI (Hybrid Local-Cloud Android Agent)

## 1. Gambaran Umum
NEXUS AI dirancang khusus sebagai **Personal Android AI Agent** dengan arsitektur hybrid dua runtime yang dapat dikembangkan, dijalankan, dan dipelihara sepenuhnya dari perangkat HP Android (menggunakan **Termux**, **Acode**, dan **GitHub Actions**).

```
+-----------------------------------------------------------------------+
|                     ANDROID APPLICATION (Kotlin + Compose)            |
|  Home | Chat | Tasks | Skills | Settings + 9 Halaman Kendali Pendukung|
|  Storage Access Framework (SAF) | Intents | ForegroundService         |
+-------------------+-----------------------------------+---------------+
                    | REST API & WebSocket (/ws)        | HTTPS Sync/Failover
                    | Bearer Token Auth (127.0.0.1:8765)|
                    v                                   v
+---------------------------------------+   +---------------------------+
|      LOCAL RUNTIME (Termux di HP)     |   |   CLOUD RUNTIME (Opsional)|
|  - FastAPI + Uvicorn + WebSocket Hub  |   |  - Cloud Agent Engine     |
|  - 8-Step Agent Workflow & Planner    |   |  - Cloud Task Queue       |
|  - Multi-Provider AI Manager + Crypto |<->|  - Task Dispatcher        |
|  - SQLite (14 Tabel) + FTS5 Memory    |   |  - Memory Sync & Failover |
|  - Tools (File, Terminal, Web, Coding)|   +---------------------------+
|  - Modular Skills (SKILL.md) + Scanner|
+---------------------------------------+
```

## 2. Alur Kerja 8-Tahap (8-Step Agent Workflow)
Setiap perintah bahasa alami yang dikirim pengguna diproses secara berurutan melalui 8 fase:
1. **Understand**: Menganalisis maksud perintah, parameter, dan mengklasifikasikan tingkat risiko (`low`, `medium`, `high`, `critical`).
2. **Plan**: Menyusun langkah kerja dan menentukan tool/skill yang diperlukan.
3. **Select Runtime**: Memilih `local` (utama saat HP aktif) atau `cloud`. Jika tugas membutuhkan file/sensor lokal HP, sistem otomatis menolak klaim eksekusi cloud offline dan mengarahkannya ke Local Runtime.
4. **Select Model**: Memilih model secara otomatis (`auto_model_routing`) atau manual dari provider yang aktif (Gemini, Qwen, DeepSeek, OpenAI, Custom API) dengan mekanisme fallback otomatis.
5. **Execute**: Menjalankan tool dalam batas izin. Untuk tindakan `high` atau `critical`, eksekusi dijeda (`awaiting_approval`) dan dikirim ke **Approval Center**.
6. **Verify**: Memeriksa integritas hasil, kode keluar terminal, dan ketersediaan file output.
7. **Remember**: Menyimpan konteks tugas ke tabel `memories` dan indeks pencarian cepat `memories_fts` (SQLite FTS5) setelah melewati filter redaksi rahasia/API key.
8. **Report**: Mengirimkan laporan hasil akhir dan streaming respons melalui WebSocket `/ws`.

## 3. Batasan Teknis Android & Mitigasi Realistis
- **Build APK dari HP**: Menjalankan kompilasi penuh Android SDK + Gradle + Kotlin secara lokal di dalam Termux pada HP memiliki kendala kompatibilitas arsitektur binar AAPT2/CMake dan konsumsi RAM yang ekstrem. Solusi paling bersih dan andal tanpa PC adalah mengedit kode di **Acode/Termux**, melakukan `git push` ke GitHub, dan membiarkan **GitHub Actions** (`.github/workflows/build-android-apk.yml`) mengompilasi APK secara otomatis dalam 2-3 menit.
- **Background Execution Termux**: Android 12+ menerapkan *Phantom Process Killer* dan *Doze Mode*. Oleh karena itu, NEXUS AI menggunakan `termux-wake-lock` di sisi Termux dan `NexusForegroundService` di sisi aplikasi Android, serta menyediakan opsi **Cloud Runtime** untuk pekerjaan yang harus tetap jalan ketika HP mati.
