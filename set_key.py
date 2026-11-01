import asyncio, sys
sys.path.insert(0, "engine")
from nexus.providers.manager import ProviderManager

provs = ProviderManager.list_providers()
print("\n=== DAFTAR PROVIDER NEXUS AI ===")
for i, p in enumerate(provs, 1):
    print(f"{i}. {p['name']} ({p['id']}) | Key saat ini: {p['key_masked']} | Status: {p['last_test_status']}")

choice = input("\nPilih nomor provider (1-5, default 1 untuk Google Gemini): ").strip() or "1"
idx = int(choice) - 1
selected = provs[idx]
key = input(f"Tempel API Key untuk {selected['name']}: ").strip()
if key:
    updated = ProviderManager.update_api_key(selected["id"], key)
    print("Tersimpan terenkripsi! Key Masked:", updated["key_masked"])
    res = asyncio.run(ProviderManager.test_provider_connection(selected["id"]))
    print("Hasil Uji Koneksi:", res["status"], "-", res["message"])
