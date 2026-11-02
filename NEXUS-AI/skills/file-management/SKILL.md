---
name: smart-file-manager
description: Mengelola file dan dokumen di dalam workspace Android, mengorganisasi folder unduhan, membuat salinan backup, dan menganalisis isi dokumen.
version: 1.0.0
required_tools:
  - file_list
  - file_read
  - file_write
  - file_move
  - file_organize
  - file_analyze
required_permissions:
  - workspace.read
  - workspace.write
  - android.saf
enabled: true
---
# Smart File Manager Skill

Aturan keselamatan pengelolaan file:
1. Hanya akses direktori yang telah diizinkan pengguna melalui Storage Access Framework (SAF) atau folder workspace.
2. Selalu buat salinan backup sebelum mengubah atau memindahkan file penting.
