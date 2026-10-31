---
name: deep-web-research
description: Melakukan pencarian web multi-sumber, mengekstrak konten halaman, membandingkan fakta, dan menyimpan laporan riset ke folder workspace/documents.
version: 1.0.0
required_tools:
  - web_search
  - web_read
  - file_write
required_permissions:
  - internet.access
  - workspace.write
enabled: true
---
# Deep Web Research Skill

Gunakan skill ini untuk melakukan riset terstruktur:
1. Jalankan pencarian kata kunci utama melalui `web_search`.
2. Baca dan bandingkan konten halaman menggunakan `web_read`.
3. Simpan rangkuman terstruktur beserta daftar sumber ke `workspace/documents/`.
