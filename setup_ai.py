import asyncio, sys
sys.path.insert(0, "engine")
from nexus.database.db import init_db, db_session, set_setting, _utc_now
from nexus.security.crypto import encrypt_secret, mask_api_key
from nexus.providers.manager import ProviderManager

init_db()
provs = ProviderManager.list_providers()

print("\n==============================================")
print("   SETUP PROVIDER, ENDPOINT, MODEL & API KEY  ")
print("==============================================")
for i, p in enumerate(provs, 1):
    print(f"{i}. {p['name']} [{p['provider_type']}]")
    print(f"   Endpoint : {p['base_url']}")
    print(f"   API Key  : {p['key_masked']}\n")

choice = input("Pilih nomor Provider (1-5, default 1): ").strip() or "1"
idx = max(0, min(len(provs) - 1, int(choice) - 1))
selected = provs[idx]
pid = selected["id"]

new_url = input(f"Masukkan Endpoint / Base URL (Tekan Enter untuk tetap '{selected['base_url']}'): ").strip()
final_url = (new_url or selected["base_url"]).rstrip("/")

default_models = {
    "prov-gemini": "gemini-2.5-flash",
    "prov-qwen": "qwen-max",
    "prov-deepseek": "deepseek-chat",
    "prov-openai": "gpt-4o-mini",
    "prov-custom": "gpt-4o-mini",
}
def_mod = default_models.get(pid, "gemini-2.5-flash")
new_model = input(f"Masukkan Nama Model ID (Tekan Enter untuk '{def_mod}'): ").strip() or def_mod

new_key = input(f"Tempel API Key untuk {selected['name']}: ").strip()

now = _utc_now()
with db_session() as conn:
    if new_key:
        enc = encrypt_secret(new_key)
        masked = mask_api_key(new_key)
        conn.execute(
            "UPDATE providers SET base_url = ?, encrypted_api_key = ?, key_masked = ?, enabled = 1, priority = 1, last_test_status = 'configured', updated_at = ? WHERE id = ?",
            (final_url, enc, masked, now, pid),
        )
    else:
        conn.execute(
            "UPDATE providers SET base_url = ?, enabled = 1, priority = 1, updated_at = ? WHERE id = ?",
            (final_url, now, pid),
        )
    conn.execute("UPDATE models SET is_default = 0")
    row_m = conn.execute("SELECT id FROM models WHERE provider_id = ? LIMIT 1", (pid,)).fetchone()
    if row_m:
        conn.execute(
            "UPDATE models SET model_name = ?, display_name = ?, is_default = 1, enabled = 1 WHERE id = ?",
            (new_model, f"{selected['name']} ({new_model})", row_m["id"]),
        )

set_setting("auto_model_routing", False)
print("\n[OK] Konfigurasi berhasil disimpan ke database!")
print(f"     Provider : {selected['name']}")
print(f"     Endpoint : {final_url}")
print(f"     Model ID : {new_model}")
if new_key:
    print(f"     API Key  : {mask_api_key(new_key)}")

print("\nMenguji panggilan ke AI Provider...")
try:
    reply, used = asyncio.run(ProviderManager.generate_completion("Halo! Jawab singkat 1 kalimat saja bahwa koneksi NEXUS AI berhasil."))
    print("\n=== BALASAN DARI AI ===")
    print(reply)
    print("=======================")
except Exception as e:
    print("Gagal memanggil AI:", e)
