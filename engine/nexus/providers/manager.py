import json
import uuid
import httpx
from typing import Any, Dict, List, Optional, Tuple
from nexus.database.db import _utc_now, db_session, get_setting, log_activity, set_setting
from nexus.security.crypto import decrypt_secret, encrypt_secret, mask_api_key

class ProviderManager:
    @staticmethod
    def list_providers() -> List[Dict[str, Any]]:
        with db_session() as conn:
            rows = conn.execute("SELECT * FROM providers ORDER BY priority ASC, name ASC").fetchall()
            result = []
            for r in rows:
                d = dict(r)
                d.pop("encrypted_api_key", None)
                d["enabled"] = bool(d["enabled"])
                result.append(d)
            return result

    @staticmethod
    def add_provider(name: str, provider_type: str, base_url: str, api_key: str = "", priority: int = 10, rate_limit_rpm: int = 60, default_model_name: Optional[str] = None) -> Dict[str, Any]:
        pid = f"prov-{uuid.uuid4().hex[:8]}"
        now = _utc_now()
        clean_key = (api_key or "").strip()
        enc = encrypt_secret(clean_key) if clean_key else ""
        masked = mask_api_key(clean_key) if clean_key else "Not configured"
        with db_session() as conn:
            conn.execute(
                "INSERT INTO providers (id, name, provider_type, base_url, encrypted_api_key, key_masked, enabled, priority, rate_limit_rpm, usage_count, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, 0, ?, ?)",
                (pid, name, provider_type.lower(), base_url.strip().rstrip("/"), enc, masked, priority, rate_limit_rpm, now, now),
            )
            if default_model_name:
                mid = f"mod-{uuid.uuid4().hex[:8]}"
                conn.execute(
                    "INSERT INTO models (id, provider_id, model_name, display_name, capabilities_json, context_window, is_default, enabled, created_at) VALUES (?, ?, ?, ?, '[\"chat\",\"planning\",\"coding\",\"tools\"]', 128000, 0, 1, ?)",
                    (mid, pid, default_model_name.strip(), f"{name} - {default_model_name.strip()}", now),
                )
        return ProviderManager.get_provider(pid)

    @staticmethod
    def get_provider(provider_id: str) -> Optional[Dict[str, Any]]:
        with db_session() as conn:
            row = conn.execute("SELECT * FROM providers WHERE id = ?", (provider_id,)).fetchone()
            if not row:
                return None
            d = dict(row)
            d.pop("encrypted_api_key", None)
            d["enabled"] = bool(d["enabled"])
            return d

    @staticmethod
    def update_api_key(provider_id: str, api_key: str, base_url: Optional[str] = None, enabled: Optional[bool] = None, model_name: Optional[str] = None) -> Optional[Dict[str, Any]]:
        now = _utc_now()
        clean_key = (api_key or "").strip()
        with db_session() as conn:
            row = conn.execute("SELECT * FROM providers WHERE id = ?", (provider_id,)).fetchone()
            if not row:
                return None
            if clean_key:
                enc = encrypt_secret(clean_key)
                masked = mask_api_key(clean_key)
                test_st = "key_saved_ready"
            else:
                enc = row["encrypted_api_key"]
                masked = row["key_masked"]
                test_st = row["last_test_status"]
            new_url = base_url.strip().rstrip("/") if (base_url and base_url.strip()) else row["base_url"]
            new_enabled = int(enabled) if enabled is not None else row["enabled"]
            conn.execute(
                "UPDATE providers SET encrypted_api_key = ?, key_masked = ?, base_url = ?, enabled = ?, last_test_status = ?, updated_at = ? WHERE id = ?",
                (enc, masked, new_url, new_enabled, test_st, now, provider_id),
            )
            if model_name and model_name.strip():
                m_clean = model_name.strip()
                conn.execute("UPDATE models SET is_default = 0")
                mrow = conn.execute("SELECT id FROM models WHERE provider_id = ? LIMIT 1", (provider_id,)).fetchone()
                if mrow:
                    conn.execute(
                        "UPDATE models SET model_name = ?, display_name = ?, is_default = 1, enabled = 1 WHERE id = ?",
                        (m_clean, f"{row['name']} ({m_clean})", mrow["id"]),
                    )
        if model_name and model_name.strip():
            set_setting("auto_model_routing", False)
        return ProviderManager.get_provider(provider_id)

    @staticmethod
    def delete_provider(provider_id: str) -> bool:
        with db_session() as conn:
            cur = conn.execute("DELETE FROM providers WHERE id = ?", (provider_id,))
            return cur.rowcount > 0

    @staticmethod
    def list_models() -> List[Dict[str, Any]]:
        with db_session() as conn:
            rows = conn.execute(
                "SELECT m.*, p.name as provider_name, p.provider_type, p.enabled as provider_enabled, p.key_masked FROM models m JOIN providers p ON p.id = m.provider_id ORDER BY p.priority ASC, m.id ASC"
            ).fetchall()
            out = []
            for r in rows:
                d = dict(r)
                d["capabilities"] = json.loads(d.pop("capabilities_json", "[]"))
                d["is_default"] = bool(d["is_default"])
                d["enabled"] = bool(d["enabled"])
                out.append(d)
            return out

    @staticmethod
    def set_default_model(model_id: str) -> bool:
        with db_session() as conn:
            row = conn.execute("SELECT id, provider_id FROM models WHERE id = ?", (model_id,)).fetchone()
            if not row:
                return False
            conn.execute("UPDATE models SET is_default = 0")
            conn.execute("UPDATE models SET is_default = 1 WHERE id = ?", (model_id,))
            conn.execute("UPDATE providers SET priority = 10 WHERE id != ?", (row["provider_id"],))
            conn.execute("UPDATE providers SET priority = 1, enabled = 1 WHERE id = ?", (row["provider_id"],))
        set_setting("auto_model_routing", False)
        return True

    @staticmethod
    def select_model_for_task(task_type: str = "chat", preferred_model_id: Optional[str] = None) -> Dict[str, Any]:
        models = ProviderManager.list_models()
        if preferred_model_id:
            for m in models:
                if m["id"] == preferred_model_id and m["enabled"]:
                    return m
        for m in models:
            if m["is_default"] and m["key_masked"] != "Not configured":
                return m
        configured = [m for m in models if m["enabled"] and m["provider_enabled"] and m["key_masked"] != "Not configured"]
        if configured:
            return configured[0]
        for m in models:
            if m["is_default"]:
                return m
        return models[0]

    @staticmethod
    async def test_provider_connection(provider_id: str) -> Dict[str, Any]:
        now = _utc_now()
        with db_session() as conn:
            row = conn.execute("SELECT * FROM providers WHERE id = ?", (provider_id,)).fetchone()
            if not row:
                return {"ok": False, "status": "not_found", "message": "Provider tidak ditemukan."}
            prov = dict(row)
            mrow = conn.execute("SELECT model_name FROM models WHERE provider_id = ? ORDER BY is_default DESC LIMIT 1", (provider_id,)).fetchone()
            model_name = mrow["model_name"] if mrow else "gpt-4o-mini"

        api_key = decrypt_secret(prov["encrypted_api_key"])
        if not api_key:
            status_msg = "missing_api_key"
            with db_session() as conn:
                conn.execute("UPDATE providers SET last_tested_at = ?, last_test_status = ? WHERE id = ?", (now, status_msg, provider_id))
            return {"ok": False, "provider_id": provider_id, "status": status_msg, "message": f"API key belum diisi untuk {prov['name']}."}

        try:
            reply = await ProviderManager._call_remote_provider(prov, model_name, api_key, "Jawab singkat: Koneksi NEXUS AI Berhasil!", "System")
            ok = True
            status_str = "connected"
            msg = f"BERHASIL terhubung ke {prov['name']} (Model: {model_name})!\nBalasan AI: {reply[:120]}"
        except Exception as exc:
            ok = False
            status_str = f"failed: {str(exc)[:50]}"
            msg = f"GAGAL menghubungi {prov['name']} (Model: {model_name}):\n{exc}"

        with db_session() as conn:
            conn.execute("UPDATE providers SET last_tested_at = ?, last_test_status = ? WHERE id = ?", (now, status_str, provider_id))
        return {"ok": ok, "provider_id": provider_id, "status": status_str, "tested_at": now, "message": msg}

    @staticmethod
    async def generate_completion(prompt: str, system_instruction: str = "", preferred_model_id: Optional[str] = None, task_type: str = "chat", context_summary: str = "") -> Tuple[str, Dict[str, Any]]:
        selected = ProviderManager.select_model_for_task(task_type=task_type, preferred_model_id=preferred_model_id)
        all_models = ProviderManager.list_models()
        candidates = []
        for m in [selected] + [x for x in all_models if x["id"] != selected.get("id")]:
            if m.get("key_masked") and m.get("key_masked") != "Not configured":
                candidates.append(m)

        full_user_prompt = prompt
        if context_summary and context_summary != "Menjalankan penalaran kontekstual AI Agent.":
            full_user_prompt = f"Perintah Pengguna: {prompt}\n\nHasil Eksekusi Tool Lokal:\n{context_summary}\n\nBerikan jawaban yang jelas berdasarkan hasil di atas."

        any_key_configured = len(candidates) > 0
        last_error_msg = ""

        for candidate in candidates[:2]:
            with db_session() as conn:
                prow = conn.execute("SELECT * FROM providers WHERE id = ?", (candidate["provider_id"],)).fetchone()
            if not prow:
                continue
            api_key = decrypt_secret(prow["encrypted_api_key"])
            if not api_key:
                continue
            try:
                text = await ProviderManager._call_remote_provider(dict(prow), candidate["model_name"], api_key, full_user_prompt, system_instruction)
                with db_session() as conn:
                    conn.execute("UPDATE providers SET usage_count = usage_count + 1, last_test_status = 'connected', updated_at = ? WHERE id = ?", (_utc_now(), prow["id"]))
                return text, candidate
            except Exception as exc:
                last_error_msg = f"{prow['name']} ({candidate['model_name']}): {exc}"
                continue

        if context_summary and context_summary != "Menjalankan penalaran kontekstual AI Agent.":
            return f"[Hasil Eksekusi Tool Lokal]\n{context_summary}", selected
        if not any_key_configured:
            return "⚠️ API Key belum diatur. Buka http://127.0.0.1:8765/panel di browser Chrome HP kamu untuk mengisi Endpoint, Model ID, dan API Key.", selected
        return f"⚠️ Gagal memanggil AI: {last_error_msg}\nCek konfigurasi di http://127.0.0.1:8765/panel", selected

    @staticmethod
    async def _call_remote_provider(prov: Dict[str, Any], model_name: str, api_key: str, prompt: str, system_instruction: str) -> str:
        base_url = prov["base_url"].strip().rstrip("/")
        is_native_gemini = ("generativelanguage.googleapis.com" in base_url) and ("/openai" not in base_url)

        async with httpx.AsyncClient(timeout=15.0, follow_redirects=True) as client:
            if is_native_gemini:
                gemini_models = [model_name]
                for fb in ("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash"):
                    if fb not in gemini_models:
                        gemini_models.append(fb)
                last_err = ""
                for gm in gemini_models:
                    url = f"{base_url}/models/{gm}:generateContent?key={api_key}"
                    payload = {"contents": [{"role": "user", "parts": [{"text": f"{system_instruction}\n\n{prompt}".strip()}]}]}
                    r = await client.post(url, json=payload)
                    if r.status_code == 200:
                        return r.json()["candidates"][0]["content"]["parts"][0]["text"]
                    last_err = f"HTTP {r.status_code}: {r.text[:160]}"
                raise RuntimeError(last_err)
            else:
                url = base_url if base_url.endswith("/chat/completions") else f"{base_url}/chat/completions"
                headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
                messages = []
                if system_instruction:
                    messages.append({"role": "system", "content": system_instruction})
                messages.append({"role": "user", "content": prompt})
                r = await client.post(url, headers=headers, json={"model": model_name, "messages": messages})
                if r.status_code != 200:
                    raise RuntimeError(f"HTTP {r.status_code}: {r.text[:160]}")
                return r.json()["choices"][0]["message"]["content"]
