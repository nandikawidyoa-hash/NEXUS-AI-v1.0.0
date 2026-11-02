# Dokumentasi REST API & WebSocket NEXUS AI

- **Base URL Lokal Default**: `http://127.0.0.1:8765`
- **Autentikasi Wajib**: Seluruh endpoint REST dan WebSocket mewajibkan header `Authorization: Bearer <NEXUS_API_TOKEN>` atau `X-Nexus-Token: <NEXUS_API_TOKEN>`.

## Daftar Endpoint Utama

| Metode | Endpoint | Deskripsi |
| :--- | :--- | :--- |
| `GET` | `/health` | Memeriksa kesehatan engine, versi, dan status darurat `agent_stopped`. |
| `GET` | `/system/info` | Informasi OS, Termux, jumlah tugas/memory, status hybrid runtime, dan baterai HP. |
| `POST` | `/chat` | Mengirim perintah natural language dan menjalankan 8-Step Agent Workflow. |
| `GET` | `/conversations` | Menampilkan daftar riwayat percakapan. |
| `GET` | `/conversations/{id}` | Menampilkan detail percakapan beserta seluruh pesan (`messages`). |
| `GET` | `/tasks` | Menampilkan daftar tugas beserta rencana 8 tahap. |
| `POST` | `/tasks` | Membuat dan menjalankan tugas baru secara eksplisit. |
| `GET` | `/tasks/{id}` | Detail lengkap tugas, langkah (`task_steps`), `tool_calls`, dan `approvals`. |
| `POST` | `/tasks/{id}/cancel` | Membatalkan tugas yang sedang berjalan. |
| `POST` | `/agent/stop` | Tombol darurat **Stop Agent**: menghentikan seluruh tugas aktif seketika. |
| `POST` | `/agent/resume` | Mengaktifkan kembali Agent setelah dihentikan darurat. |
| `GET` | `/providers` | Daftar provider AI (API key selalu disamarkan/masked). |
| `POST` | `/providers` | Menambahkan provider AI baru (termasuk Custom OpenAI-compatible). |
| `PUT` | `/providers/{id}/key` | Menyimpan/mengganti API key (dienkripsi dengan Fernet AES). |
| `POST` | `/providers/test` | Menguji koneksi ke endpoint provider AI. |
| `GET` | `/models` | Daftar seluruh model AI dan status `auto_model_routing`. |
| `GET` | `/tools` | Daftar 14 built-in tools beserta kategori dan tingkat risikonya. |
| `POST` | `/tools/execute` | Menjalankan tool secara langsung dengan proteksi izin & backup otomatis. |
| `POST` | `/tools/rollback` | Mengembalikan file ke versi sebelum perubahan dari folder `workspace/backups`. |
| `GET` | `/skills` | Daftar skill modular berbasis `SKILL.md` dan status keamanannya. |
| `POST` | `/skills/install` | Memasang skill baru setelah diperiksa oleh *Skill Security Scanner*. |
| `GET` | `/memories` | Menampilkan memory berdasarkan kategori (`short_term`, `long_term`, `project`, `task`). |
| `GET` | `/memories/search?q=` | Pencarian teks cepat menggunakan indeks virtual **SQLite FTS5**. |
| `GET` | `/automations` | Daftar tugas terjadwal beserta peringatan batasan background Android. |
| `POST` | `/automations` | Membuat jadwal tugas otomatis baru. |
| `GET` | `/approvals` | Daftar permintaan persetujuan tindakan berisiko tinggi (`high`/`critical`). |
| `POST` | `/approvals/{id}` | Menyetujui atau menolak tindakan berisiko tinggi. |
| `GET` | `/logs` | Menampilkan catatan audit aktivitas (`activity_logs`). |

## WebSocket Endpoint (`WS /ws`)
Hubungkan ke `ws://127.0.0.1:8765/ws?token=<NEXUS_API_TOKEN>` atau sertakan header `Authorization`.
Event yang dipancarkan secara real-time:
- `connected`: Konfirmasi koneksi berhasil.
- `chat_stream`: Potongan teks streaming (`delta`, `done`) saat Agent menyusun jawaban.
- `task_progress`: Pembaruan fase 1–8 (`understand` s/d `report`) secara langsung.
- `approval_requested`: Notifikasi instan ketika ada tindakan `high`/`critical` yang membutuhkan persetujuan.
- `terminal_output`: Output `stdout`/`stderr` dari eksekusi perintah terminal.
- `agent_stopped`: Sinyal darurat ketika pengguna menekan tombol **Stop Agent**.
