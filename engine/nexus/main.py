import uvicorn
from fastapi import FastAPI, Request, WebSocket, WebSocketDisconnect, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse, JSONResponse

from nexus import __app_name__, __version__
from nexus.api.routes import router
from nexus.api.ws import ws_hub
from nexus.config import HOST, PORT
from nexus.database.db import db_session, init_db
from nexus.providers.manager import ProviderManager
from nexus.security.auth import verify_token_string
from nexus.skills.manager import SkillManager

PANEL_HTML = """<!DOCTYPE html>
<html lang="id">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>NEXUS AI — Pengaturan Endpoint, Model & API Key</title>
<style>
  body { background:#090d16; color:#f3f4f6; font-family:sans-serif; margin:0; padding:16px; }
  .card { background:#111827; border:1px solid #1f2937; border-radius:16px; padding:16px; margin-bottom:14px; }
  h2 { margin:0 0 6px 0; font-size:20px; color:#60a5fa; }
  label { display:block; font-size:13px; color:#9ca3af; margin-top:12px; margin-bottom:4px; }
  input, select { width:100%; box-sizing:border-box; padding:12px; border-radius:10px; border:1px solid #374151; background:#1f2937; color:#fff; font-size:14px; }
  button { width:100%; margin-top:16px; padding:14px; border:none; border-radius:12px; background:#2563eb; color:#fff; font-weight:bold; font-size:15px; cursor:pointer; }
  .chips { display:flex; flex-wrap:wrap; gap:6px; margin-top:8px; }
  .chip { background:#1f2937; border:1px solid #374151; color:#d1d5db; padding:6px 10px; border-radius:8px; font-size:12px; cursor:pointer; }
  .status { margin-top:12px; padding:12px; border-radius:10px; background:#1f2937; font-size:13px; white-space:pre-wrap; word-break:break-word; }
  .ok { border-left:4px solid #10b981; }
  .err { border-left:4px solid #ef4444; }
</style>
</head>
<body>
  <div class="card">
    <h2>NEXUS AI — Pengaturan AI Provider</h2>
    <div style="font-size:13px;color:#9ca3af;">Atur Endpoint URL, Nama Model (bebas diketik), dan API Key. Setelah disimpan di sini, aplikasi Android NEXUS AI langsung otomatis memakai konfigurasi ini.</div>

    <label>1. Pilih Provider / Preset</label>
    <select id="prov" onchange="applyPreset()">
      <option value="prov-gemini|https://generativelanguage.googleapis.com/v1beta|gemini-2.5-flash">Google Gemini (Native API)</option>
      <option value="prov-custom|https://openrouter.ai/api/v1|google/gemini-2.5-flash">OpenRouter / Custom OpenAI-Compatible</option>
      <option value="prov-deepseek|https://api.deepseek.com/v1|deepseek-chat">DeepSeek API</option>
      <option value="prov-openai|https://api.openai.com/v1|gpt-4o-mini">OpenAI API</option>
      <option value="prov-qwen|https://dashscope-intl.aliyuncs.com/compatible-mode/v1|qwen-max">Alibaba Qwen (DashScope)</option>
    </select>

    <label>2. Endpoint / Base URL (Bisa Diedit Bebas)</label>
    <input id="base_url" type="text" value="https://generativelanguage.googleapis.com/v1beta" placeholder="https://...">

    <label>3. Nama Model / Model ID (Bisa Diketik Bebas)</label>
    <input id="model_name" type="text" value="gemini-2.5-flash" placeholder="Ketik model ID apa saja...">
    <div class="chips">
      <span class="chip" onclick="setModel('gemini-2.5-flash')">gemini-2.5-flash</span>
      <span class="chip" onclick="setModel('gemini-2.0-flash')">gemini-2.0-flash</span>
      <span class="chip" onclick="setModel('deepseek-chat')">deepseek-chat</span>
      <span class="chip" onclick="setModel('gpt-4o-mini')">gpt-4o-mini</span>
      <span class="chip" onclick="setModel('qwen-max')">qwen-max</span>
    </div>

    <label>4. API Key</label>
    <input id="api_key" type="password" placeholder="Tempel API Key di sini (AIza... atau sk-...)">

    <button onclick="saveAndTest()">Simpan & Tes Koneksi AI Sekarang</button>
    <div id="result" class="status" style="display:none;"></div>
  </div>

  <div class="card">
    <h2>Status Konfigurasi Aktif</h2>
    <div id="active_info" style="font-size:13px;color:#d1d5db;white-space:pre-wrap;">Memuat...</div>
  </div>

<script>
function applyPreset() {
  const val = document.getElementById('prov').value.split('|');
  document.getElementById('base_url').value = val[1];
  document.getElementById('model_name').value = val[2];
}
function setModel(m) {
  document.getElementById('model_name').value = m;
}
async function loadActive() {
  const r = await fetch('/panel/state');
  const d = await r.json();
  document.getElementById('active_info').innerText =
    'Model Aktif : ' + d.active_model.display_name + ' (' + d.active_model.model_name + ')\\n' +
    'Provider    : ' + d.active_provider.name + '\\n' +
    'Endpoint    : ' + d.active_provider.base_url + '\\n' +
    'API Key     : ' + d.active_provider.key_masked + '\\n' +
    'Status Uji  : ' + d.active_provider.last_test_status;
}
async function saveAndTest() {
  const resBox = document.getElementById('result');
  resBox.style.display = 'block';
  resBox.className = 'status';
  resBox.innerText = 'Menyimpan konfigurasi dan menguji panggilan ke AI...';
  const pid = document.getElementById('prov').value.split('|')[0];
  const payload = {
    provider_id: pid,
    base_url: document.getElementById('base_url').value.trim(),
    model_name: document.getElementById('model_name').value.trim(),
    api_key: document.getElementById('api_key').value.trim()
  };
  try {
    const r = await fetch('/panel/save', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(payload)
    });
    const d = await r.json();
    resBox.className = 'status ' + (d.ok ? 'ok' : 'err');
    resBox.innerText = d.message;
    document.getElementById('api_key').value = '';
    loadActive();
  } catch (e) {
    resBox.className = 'status err';
    resBox.innerText = 'Gagal menghubungi server lokal: ' + e;
  }
}
loadActive();
</script>
</body>
</html>"""

def create_app() -> FastAPI:
    init_db()
    SkillManager.sync_filesystem_skills()
    app = FastAPI(title=__app_name__, version=__version__)
    app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_credentials=True, allow_methods=["*"], allow_headers=["*"])

    @app.get("/panel", response_class=HTMLResponse)
    async def web_control_panel():
        return PANEL_HTML

    @app.get("/panel/state")
    async def web_panel_state():
        mod = ProviderManager.select_model_for_task("chat")
        prov = ProviderManager.get_provider(mod["provider_id"]) or {"name": "Default", "base_url": "", "key_masked": "Not configured", "last_test_status": "untested"}
        return {"active_model": mod, "active_provider": prov}

    @app.post("/panel/save")
    async def web_panel_save(req: Request):
        body = await req.json()
        pid = body.get("provider_id", "prov-gemini")
        base_url = body.get("base_url", "").strip()
        model_name = body.get("model_name", "").strip()
        api_key = body.get("api_key", "").strip()
        ProviderManager.update_api_key(provider_id=pid, api_key=api_key, base_url=base_url, enabled=True, model_name=model_name)
        with db_session() as conn:
            mrow = conn.execute("SELECT id FROM models WHERE provider_id = ? LIMIT 1", (pid,)).fetchone()
            if mrow:
                ProviderManager.set_default_model(mrow["id"])
        test_res = await ProviderManager.test_provider_connection(pid)
        return JSONResponse(test_res)

    app.include_router(router)

    @app.websocket("/ws")
    async def websocket_endpoint(websocket: WebSocket):
        token = websocket.headers.get("authorization") or websocket.headers.get("x-nexus-token") or websocket.query_params.get("token")
        if not verify_token_string(token):
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
            return
        await ws_hub.connect(websocket)
        try:
            await websocket.send_json({"event": "connected", "data": {"service": __app_name__, "version": __version__}})
            while True:
                msg = await websocket.receive_text()
                await websocket.send_json({"event": "pong", "data": {"echo": msg}})
        except Exception:
            ws_hub.disconnect(websocket)

    return app

app = create_app()

if __name__ == "__main__":
    uvicorn.run("nexus.main:app", host=HOST, port=PORT, reload=False)
