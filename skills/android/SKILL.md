---
name: android-device-assistant
description: Mengintegrasikan kemampuan perangkat Android (membuka aplikasi via Intent, membaca status baterai, mengirim notifikasi tugas, dan mengelola clipboard dengan izin).
version: 1.0.0
required_tools:
  - file_list
  - terminal_exec
required_permissions:
  - android.intents
  - android.notifications
  - android.battery
  - android.clipboard
enabled: true
---
# Android Device Assistant Skill

Panduan integrasi perangkat Android:
1. Gunakan Android Intents resmi untuk membuka aplikasi eksternal (WhatsApp, Browser, Maps, Kamera).
2. Hormati batasan sistem operasi Android: jangan mengklaim akses root atau kontrol latar belakang tanpa Foreground Service.
