# Kebijakan & Arsitektur Keamanan NEXUS AI

## 1. Model Keamanan Semi-Autonomous (4 Tingkat Risiko)
- **Low Risk (Otomatis)**: Membaca file di dalam workspace yang diizinkan, mencari file, menganalisis dokumen, riset web, dan memeriksa sintaks kode.
- **Medium Risk (Otomatis dalam Batas Izin + Backup Wajib)**: Membuat, mengedit, memindahkan, atau mengorganisasi file di dalam workspace, serta menjalankan perintah terminal standar non-root. Setiap perubahan pada file yang sudah ada otomatis membuat salinan `.bak` di `workspace/backups/` sehingga dapat di-*rollback* kapan saja.
- **High Risk (Wajib Persetujuan Pengguna di Approval Center)**: Menghapus banyak file sekaligus, penghapusan rekursif, atau perintah terminal berisiko tinggi. Tugas otomatis dijeda dengan status `awaiting_approval`.
- **Critical Risk (Diblokir dari Otomasi)**: Transaksi keuangan, pembayaran, transfer bank, atau penggunaan kredensial pembayaran tidak pernah dijalankan secara otomatis.

## 2. Proteksi API Key & Anti-Kebocoran Rahasia
- **Enkripsi Penyimpanan**: Seluruh API key provider AI dienkripsi menggunakan `cryptography.fernet` (AES-128-CBC + HMAC-SHA256) sebelum disimpan ke kolom `encrypted_api_key` di SQLite.
- **Masking Ketat**: Endpoint API dan UI Android hanya menerima versi tersamar (`sk-p...cdef`), tidak pernah menampilkan API key secara utuh di layar maupun di log.
- **Redaksi Memory Otomatis**: `MemoryManager` memindai setiap teks yang akan disimpan ke SQLite Memory menggunakan pola deteksi rahasia (`sk-...`, `AIza...`, `api_key=...`, `bearer ...`) dan menyamarkannya menjadi `[REDACTED_SECRET]`.

## 3. Pembatasan Workspace & Non-Root Terminal
- `ToolManager._resolve_safe_path()` memvalidasi setiap path terhadap daftar `allowed_directories` (secara default terkunci di dalam folder `workspace/` dan direktori SAF yang dipilih pengguna). Upaya *path traversal* (`../../`) keluar dari workspace langsung ditolak dengan `PermissionError`.
- Eksekusi terminal menolak perintah `sudo`, `su`, `rm -rf /`, `mkfs`, dan `dd if=`, serta menerapkan batas waktu (*timeout*) ketat.
