# Panduan Instalasi, Build APK dari HP Android, & Troubleshooting

Panduan ini disusun khusus untuk pengguna yang mengerjakan seluruh proses **100% melalui HP Android** (tanpa PC atau Android Studio desktop).

---

## Bagian A — Menjalankan Local Engine di Termux

1. **Pasang Termux & Acode**
   - Pasang **Termux** (disarankan versi F-Droid/GitHub resmi) dan **Acode** sebagai editor kode di HP Android Anda.
2. **Ekstrak Project NEXUS AI**
   ```bash
   mkdir -p ~/projects
   cd ~/projects
   unzip ~/storage/downloads/NEXUS-AI-v1.0.0-complete.zip -d NEXUS-AI
   cd NEXUS-AI
   ```
3. **Jalankan Skrip Instalasi Otomatis**
   ```bash
   bash scripts/install.sh
   ```
   Skrip ini akan memasang dependensi Python, menginisialisasi 14 tabel SQLite + indeks FTS5, memuat 4 skill bawaan, dan menampilkan **Auth Token** lokal Anda.
4. **Nyalakan Local Runtime Engine**
   ```bash
   bash scripts/start.sh
   bash scripts/status.sh
   ```
   Engine kini berjalan di `http://127.0.0.1:8765`.

---

## Bagian B — Build APK Android Tanpa PC (Via GitHub Actions dari HP)

Karena mengompilasi Android SDK + Gradle secara penuh di dalam Termux berisiko gagal akibat keterbatasan RAM HP dan binari AAPT2 arsitektur ARM64, project ini telah dilengkapi pipeline otomatis `.github/workflows/build-android-apk.yml`.

1. **Buat Repository Baru di GitHub** melalui browser HP Anda (misalnya `nexus-ai-android`).
2. **Push Kode dari Termux ke GitHub**:
   ```bash
   cd ~/projects/NEXUS-AI
   git init
   git branch -M main
   git add .
   git commit -m "Initial commit: NEXUS AI Hybrid Android Agent v1.0.0"
   git remote add origin https://github.com/<USERNAME_ANDA>/<NAMA_REPO>.git
   git push -u origin main
   ```
3. **Unduh APK Hasil Build**:
   - Buka tab **Actions** di repository GitHub Anda melalui browser HP.
   - Tunggu workflow **Build NEXUS AI Android APK & Verify Engine** selesai (centang hijau).
   - Gulir ke bagian **Artifacts** di bagian bawah halaman workflow, lalu unduh **NEXUS-AI-Android-Debug-APK**.
   - Ekstrak file `.zip` tersebut dan instal `app-debug.apk` di HP Android Anda.

---

## Bagian C — Menghubungkan Aplikasi Android dengan Local Engine

1. Buka aplikasi **NEXUS AI** yang sudah diinstal di HP.
2. Buka tab **Settings**.
3. Pastikan **Alamat Local Runtime** berisi `http://127.0.0.1:8765`.
4. Tempelkan **Auth Token** yang muncul saat Anda menjalankan `bash scripts/install.sh` (atau lihat isi file `.env` di Termux dengan `cat .env`).
5. Ketuk **Simpan & Hubungkan Ulang**. Status di halaman **Home** akan berubah menjadi `ONLINE`.
6. Buka **Settings → Kelola Multi-Provider AI** untuk memasukkan API Key provider pilihan Anda (Gemini, Qwen, DeepSeek, OpenAI, atau Custom API).

---

## Bagian D — Panduan Troubleshooting

1. **Aplikasi menampilkan "Local Engine offline"**:
   - Buka Termux dan jalankan `bash scripts/status.sh`. Jika mati, jalankan `bash scripts/start.sh`.
   - Pastikan token di aplikasi Android sama persis dengan `NEXUS_API_TOKEN` di file `.env`.
2. **Error `401 Unauthorized` saat mengakses endpoint**:
   - Semua endpoint NEXUS AI wajib menyertakan token autentikasi. Periksa kembali token di menu **Settings**.
3. **Termux berhenti sendiri saat layar HP mati lama**:
   - Pastikan Anda mengizinkan notifikasi Termux dan menjalankan `termux-wake-lock` (otomatis dipanggil oleh `scripts/start.sh`).
   - Nonaktifkan optimasi baterai (*Battery Optimization -> Unrestricted*) untuk aplikasi **Termux** dan **NEXUS AI** di pengaturan Android.
   - Di aplikasi NEXUS AI, buka **Home → Device Bridge → Aktifkan Foreground Service**.
4. **Ingin membatalkan perubahan file yang dilakukan Agent**:
   - Salinan file asli sebelum diedit selalu disimpan di `workspace/backups/`. Anda dapat memulihkannya melalui endpoint `/tools/rollback` atau menyalinnya kembali dari **Workspace Explorer**.
