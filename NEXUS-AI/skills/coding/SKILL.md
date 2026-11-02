---
name: coding-agent
description: Menganalisis struktur project, mendeteksi bug, menulis kode modular, membuat backup sebelum mengedit, dan menjalankan verifikasi sintaks/unit test.
version: 1.0.0
required_tools:
  - code_inspect
  - file_read
  - file_write
  - file_edit
  - code_test
required_permissions:
  - workspace.read
  - workspace.write
enabled: true
---
# Coding Agent Skill

Gunakan skill ini ketika pengguna meminta pembuatan fitur baru, refactoring, atau perbaikan bug:
1. Periksa struktur project dan file yang sudah ada terlebih dahulu (`code_inspect` & `file_read`).
2. Buat backup otomatis sebelum memodifikasi file yang ada (`file_edit` / `file_write`).
3. Jalankan pengujian (`code_test`) setelah perubahan selesai dan laporkan hasilnya secara transparan.
