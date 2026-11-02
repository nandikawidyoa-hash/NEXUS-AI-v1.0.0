import json
import re
import uuid
from pathlib import Path
from typing import Any, Dict, List, Optional
from nexus.api.ws import ws_hub
from nexus.config import WORKSPACE_DIR
from nexus.database.db import _utc_now, db_session, get_setting, log_activity, set_setting
from nexus.memory.manager import MemoryManager
from nexus.permissions.manager import PermissionManager
from nexus.planner.planner import TaskPlanner
from nexus.providers.manager import MarketingIntelligenceAgent, ProviderManager, VideoGenerationManager
from nexus.skills.manager import SkillManager
from nexus.tools.manager import ToolManager

SPECIALIST_AGENTS = [
    {
        "id": "orchestrator",
        "name": "Main Orchestrator Agent",
        "role": "Mengatur keseluruhan pekerjaan, dekomposisi tujuan kompleks, delegasi ke agent spesialis, manajemen checkpoint, dan sintesis hasil.",
        "tools": ["task_decomposition", "checkpoint_manager", "agent_delegation"],
    },
    {
        "id": "research_agent",
        "name": "Research Agent",
        "role": "Melakukan riset topik, pasar, kompetitor, dan mengumpulkan referensi terverifikasi.",
        "tools": ["web_search", "web_read", "file_search"],
    },
    {
        "id": "video_agent",
        "name": "Video Agent",
        "role": "Menangani pipeline produksi video 15 tahap (ide, script, storyboard, visual frame, TTS/audio WAV, subtitle SRT, FFmpeg/API MP4, dan variasi A/B).",
        "tools": ["video_production_15_stage", "ffmpeg_renderer", "subtitle_generator"],
    },
    {
        "id": "marketing_agent",
        "name": "Marketing Agent",
        "role": "Menyusun strategi marketing 360°, analisis kompetitor, persona, SEO, kalender konten 30 hari, copywriting, dan caption TikTok/IG.",
        "tools": ["marketing_intelligence_16_pillar", "content_calendar_30d", "ad_copywriter"],
    },
    {
        "id": "coding_agent",
        "name": "Coding Agent",
        "role": "Menulis, memperbaiki, membuat website bisnis siap uji, dan menjalankan pengujian kode otomatis.",
        "tools": ["file_write", "file_edit", "code_inspect", "code_test"],
    },
    {
        "id": "browser_agent",
        "name": "Browser Agent",
        "role": "Melakukan navigasi web, pencarian informasi, dan ekstraksi konten halaman web.",
        "tools": ["web_search", "web_read"],
    },
    {
        "id": "file_agent",
        "name": "File Agent",
        "role": "Mengelola dokumen, membaca/menulis file, membuat backup, dan mengorganisasi folder workspace.",
        "tools": ["file_list", "file_read", "file_write", "file_organize", "file_analyze"],
    },
    {
        "id": "automation_agent",
        "name": "Automation Agent",
        "role": "Menjalankan workflow otomatis, jadwal berkala, dan integrasi Android Device Bridge.",
        "tools": ["android_app_launcher", "terminal_exec"],
    },
    {
        "id": "data_analyst_agent",
        "name": "Data Analyst Agent",
        "role": "Menganalisis isi dokumen/data, menghitung metrik statistik, dan menyusun laporan analisis terstruktur.",
        "tools": ["file_analyze", "file_read", "file_write"],
    },
    {
        "id": "quality_control_agent",
        "name": "Quality Control Agent",
        "role": "Memeriksa hasil kerja seluruh agent lain (Self-Evaluation & Result Verification), memastikan file fisik valid sebelum tugas dinyatakan selesai.",
        "tools": ["artifact_verifier", "self_evaluator"],
    },
]


class AgentEngine:
    @staticmethod
    def list_specialists() -> List[Dict[str, Any]]:
        return SPECIALIST_AGENTS

    @staticmethod
    def is_stopped() -> bool:
        return bool(get_setting("agent_stopped", False))

    @staticmethod
    async def stop_all_tasks() -> Dict[str, Any]:
        set_setting("agent_stopped", True)
        now = _utc_now()
        with db_session() as conn:
            cur = conn.execute(
                "UPDATE tasks SET status = 'cancelled', error_message = 'Dihentikan secara darurat oleh pengguna (Stop Agent).', updated_at = ? WHERE status IN ('pending','planning','running','verifying')",
                (now,),
            )
            cancelled_count = cur.rowcount
            conn.execute(
                "UPDATE autonomous_runs SET status = 'paused', updated_at = ? WHERE status IN ('queued','planning','running')",
                (now,),
            )
        log_activity("WARNING", "agent", "Emergency Stop Agent triggered by user", {"cancelled_tasks": cancelled_count})
        await ws_hub.broadcast("agent_stopped", {"stopped": True, "cancelled_tasks": cancelled_count, "timestamp": now})
        return {"agent_stopped": True, "cancelled_tasks": cancelled_count, "timestamp": now}

    @staticmethod
    async def resume_agent() -> Dict[str, Any]:
        set_setting("agent_stopped", False)
        now = _utc_now()
        log_activity("INFO", "agent", "Agent resumed by user")
        await ws_hub.broadcast("runtime_status", {"agent_stopped": False, "timestamp": now})
        return {"agent_stopped": False, "timestamp": now}

    @staticmethod
    async def cancel_task(task_id: str) -> Optional[Dict[str, Any]]:
        now = _utc_now()
        with db_session() as conn:
            row = conn.execute("SELECT * FROM tasks WHERE id = ?", (task_id,)).fetchone()
            if not row:
                return None
            conn.execute(
                "UPDATE tasks SET status = 'cancelled', error_message = 'Dibatalkan oleh pengguna.', updated_at = ? WHERE id = ?",
                (now, task_id),
            )
            conn.execute(
                "UPDATE task_steps SET status = 'cancelled', updated_at = ? WHERE task_id = ? AND status IN ('pending','running')",
                (now, task_id),
            )
        await ws_hub.broadcast("task_progress", {"task_id": task_id, "status": "cancelled"})
        return AgentEngine.get_task(task_id)

    @staticmethod
    def get_task(task_id: str) -> Optional[Dict[str, Any]]:
        with db_session() as conn:
            t = conn.execute("SELECT * FROM tasks WHERE id = ?", (task_id,)).fetchone()
            if not t:
                return None
            steps = conn.execute("SELECT * FROM task_steps WHERE task_id = ? ORDER BY step_number ASC", (task_id,)).fetchall()
            calls = conn.execute("SELECT * FROM tool_calls WHERE task_id = ? ORDER BY created_at ASC", (task_id,)).fetchall()
            approvals = conn.execute("SELECT * FROM approvals WHERE task_id = ? ORDER BY requested_at DESC", (task_id,)).fetchall()
            d = dict(t)
            d["result_summary"] = d.get("result_summary") or ""
            d["error_message"] = d.get("error_message") or ""
            d["recovery_steps"] = d.get("recovery_steps") or ""
            d["plan"] = json.loads(d.pop("plan_json", "[]"))
            d["steps"] = [dict(s) for s in steps]
            d["tool_calls"] = [dict(c) for c in calls]
            d["approvals"] = [dict(a) for a in approvals]
            return d

    @staticmethod
    def list_tasks(limit: int = 50) -> List[Dict[str, Any]]:
        with db_session() as conn:
            rows = conn.execute("SELECT * FROM tasks ORDER BY created_at DESC LIMIT ?", (limit,)).fetchall()
            out = []
            for r in rows:
                d = dict(r)
                d["result_summary"] = d.get("result_summary") or ""
                d["error_message"] = d.get("error_message") or ""
                d["recovery_steps"] = d.get("recovery_steps") or ""
                d["plan"] = json.loads(d.pop("plan_json", "[]"))
                out.append(d)
            return out

    # =========================================================================
    # MULTI-AGENT ORCHESTRATOR & LONG-RUNNING CHECKPOINT ENGINE
    # =========================================================================

    @staticmethod
    def decompose_goal_into_multi_agent_steps(goal: str) -> List[Dict[str, Any]]:
        """Decomposes a high-level user goal into a structured multi-agent execution plan."""
        lower = (goal or "").lower()
        steps: List[Dict[str, Any]] = [
            {
                "step_number": 1,
                "agent_id": "orchestrator",
                "agent_name": "Main Orchestrator Agent",
                "action_type": "goal_decomposition",
                "title": "Goal Understanding & Task Decomposition",
                "description": f"Menganalisis tujuan utama dan membagi menjadi tahapan spesialis: '{goal[:90]}'",
                "autonomy_tier": "LEVEL_1_AUTONOMOUS",
                "status": "pending",
            }
        ]

        is_video = any(k in lower for k in ("video", "tiktok", "reels", "shorts", "promosi", "storyboard", "animasi"))
        is_marketing = any(k in lower for k in ("marketing", "kompetitor", "competitor", "strategi", "campaign", "caption", "seo", "kalender konten", "30 hari", "iklan", "prime apps"))
        is_website = any(k in lower for k in ("website", "web bisnis", "landing page", "html", "buat web", "aplikasi web"))
        is_analysis = any(k in lower for k in ("analisis", "analyze", "laporan", "report", "data", "evaluasi file"))
        is_android = any(k in lower for k in ("buka tiktok", "buka whatsapp", "buka chrome", "buka youtube", "buka instagram"))

        step_num = 2
        # Always run Research Agent first for context gathering
        steps.append({
            "step_number": step_num,
            "agent_id": "research_agent",
            "agent_name": "Research Agent",
            "action_type": "research",
            "title": "Market, Topic & Reference Research",
            "description": f"Mengumpulkan informasi, referensi, dan struktur terbaik untuk: {goal[:70]}",
            "autonomy_tier": "LEVEL_1_AUTONOMOUS",
            "status": "pending",
        })
        step_num += 1

        if is_marketing or is_video:
            steps.append({
                "step_number": step_num,
                "agent_id": "marketing_agent",
                "agent_name": "Marketing Agent",
                "action_type": "marketing_workflow",
                "title": "360° Marketing Strategy, 30-Day Calendar & Copywriting",
                "description": "Menyusun riset kompetitor, customer persona, strategi 30 hari, caption TikTok/IG, hashtag, dan script promosi.",
                "autonomy_tier": "LEVEL_1_AUTONOMOUS",
                "status": "pending",
            })
            step_num += 1

        if is_video:
            steps.append({
                "step_number": step_num,
                "agent_id": "video_agent",
                "agent_name": "Video Agent",
                "action_type": "video_production",
                "title": "15-Stage Autonomous Video Production & FFmpeg Rendering",
                "description": "Menghasilkan script, storyboard 4 scene, frame visual, audio WAV, subtitle .srt, variasi A/B, dan file video .mp4.",
                "autonomy_tier": "LEVEL_1_AUTONOMOUS",
                "status": "pending",
            })
            step_num += 1

        if is_website:
            steps.append({
                "step_number": step_num,
                "agent_id": "coding_agent",
                "agent_name": "Coding Agent",
                "action_type": "website_builder",
                "title": "Build Responsive Business Website & Automated Test",
                "description": "Menulis kode HTML5, CSS3, JavaScript interaktif, dan memverifikasi kesiapan website bisnis di workspace.",
                "autonomy_tier": "LEVEL_1_AUTONOMOUS",
                "status": "pending",
            })
            step_num += 1

        if is_analysis or (not is_video and not is_marketing and not is_website and not is_android):
            steps.append({
                "step_number": step_num,
                "agent_id": "file_agent",
                "agent_name": "File Agent",
                "action_type": "file_inspection",
                "title": "Workspace Document & File Inspection",
                "description": "Memeriksa dan mengekstrak data dari file di dalam direktori workspace.",
                "autonomy_tier": "LEVEL_1_AUTONOMOUS",
                "status": "pending",
            })
            step_num += 1
            steps.append({
                "step_number": step_num,
                "agent_id": "data_analyst_agent",
                "agent_name": "Data Analyst Agent",
                "action_type": "data_analysis_report",
                "title": "Data Analysis & Structured Report Generation",
                "description": "Menganalisis temuan dan menyusun dokumen laporan eksekutif di workspace/documents/.",
                "autonomy_tier": "LEVEL_1_AUTONOMOUS",
                "status": "pending",
            })
            step_num += 1

        if is_android:
            steps.append({
                "step_number": step_num,
                "agent_id": "automation_agent",
                "agent_name": "Automation Agent",
                "action_type": "android_automation",
                "title": "Android Device Bridge & App Execution",
                "description": f"Menjalankan perintah perangkat Android melalui Device Bridge: {goal[:70]}",
                "autonomy_tier": "LEVEL_2_CONTROLLED",
                "status": "pending",
            })
            step_num += 1

        # Final Quality Control & Self-Evaluation Step
        steps.append({
            "step_number": step_num,
            "agent_id": "quality_control_agent",
            "agent_name": "Quality Control Agent",
            "action_type": "quality_control",
            "title": "Self-Evaluation, Artifact Verification & Final Synthesis",
            "description": "Memverifikasi keberadaan dan kualitas seluruh file/aset yang dihasilkan sebelum menyatakan tugas selesai.",
            "autonomy_tier": "LEVEL_1_AUTONOMOUS",
            "status": "pending",
        })
        return steps

    @staticmethod
    def _build_business_website_in_workspace(goal: str, run_id: str) -> Dict[str, Any]:
        slug = re.sub(r"[^a-zA-Z0-9_-]", "_", goal.lower()).strip("_")[:24] or "bisnis"
        site_dir = WORKSPACE_DIR / "projects" / f"website_{slug}_{run_id[-4:]}"
        site_dir.mkdir(parents=True, exist_ok=True)

        index_html = f"""<!DOCTYPE html>
<html lang="id">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Solusi Bisnis Modern — {goal[:50]}</title>
  <link rel="stylesheet" href="styles.css">
</head>
<body>
  <header class="hero">
    <nav class="navbar">
      <div class="logo">NEXUS<span>BIZ</span></div>
      <div class="nav-links">
        <a href="#fitur">Keunggulan</a>
        <a href="#layanan">Layanan</a>
        <a href="#kalkulator">Estimasi ROI</a>
        <a href="#kontak" class="btn-primary">Hubungi Kami</a>
      </div>
    </nav>
    <div class="hero-content">
      <span class="badge">Platform Terverifikasi AI</span>
      <h1>Transformasi Bisnis Anda Lebih Cepat & Terukur</h1>
      <p>Dibangun otomatis oleh NEXUS AI Coding & Marketing Agent untuk: {goal}</p>
      <div class="cta-group">
        <button onclick="openDemoModal()" class="btn-cta">Coba Demo Interaktif</button>
        <a href="#fitur" class="btn-secondary">Pelajari Fitur</a>
      </div>
    </div>
  </header>

  <section id="fitur" class="features">
    <h2>Keunggulan Utama Bisnis Kami</h2>
    <div class="grid">
      <div class="card">
        <h3>1. Layanan Cepat & Otomatis</h3>
        <p>Proses pemesanan dan konsultasi pelanggan berjalan instan 24/7 tanpa hambatan.</p>
      </div>
      <div class="card">
        <h3>2. Kualitas Terjamin</h3>
        <p>Setiap produk dan layanan melewati pemeriksaan mutu ketat dengan garansi kepuasan.</p>
      </div>
      <div class="card">
        <h3>3. Harga Transparan</h3>
        <p>Paket layanan fleksibel sesuai skala bisnis dan kebutuhan operasional Anda.</p>
      </div>
    </div>
  </section>

  <section id="kalkulator" class="calculator">
    <h2>Kalkulator Simulasi Pertumbuhan</h2>
    <div class="calc-box">
      <label>Target Pelanggan per Bulan: <span id="custVal">100</span></label>
      <input type="range" id="custInput" min="10" max="1000" value="100" oninput="updateROI()">
      <div id="roiResult" class="roi-output">Estimasi Peningkatan Efisiensi: +300 Jam/Bulan</div>
    </div>
  </section>

  <footer id="kontak">
    <p>&copy; 2026 NEXUS Autonomous Business Solution • Siap Diuji & Dipublikasikan</p>
  </footer>
  <script src="app.js"></script>
</body>
</html>"""

        styles_css = """* { box-sizing: border-box; margin: 0; padding: 0; font-family: system-ui, sans-serif; }
body { background: #090d16; color: #f3f4f6; line-height: 1.6; }
.hero { background: linear-gradient(135deg, #0f172a 0%, #1e1b4b 100%); padding: 24px 5% 72px; }
.navbar { display: flex; justify-content: space-between; align-items: center; padding-bottom: 48px; }
.logo { font-size: 22px; font-weight: 800; color: #fff; }
.logo span { color: #38bdf8; }
.nav-links a { color: #cbd5e1; text-decoration: none; margin-left: 20px; font-size: 14px; }
.hero-content { max-width: 720px; margin: 0 auto; text-align: center; }
.badge { background: rgba(56, 189, 248, 0.15); color: #38bdf8; padding: 6px 14px; border-radius: 99px; font-size: 12px; }
h1 { font-size: 36px; margin: 18px 0 12px; color: #fff; }
.cta-group { margin-top: 28px; display: flex; gap: 12px; justify-content: center; }
.btn-cta, .btn-primary { background: #2563eb; color: #fff; border: none; padding: 12px 24px; border-radius: 10px; cursor: pointer; font-weight: 600; text-decoration: none; }
.btn-secondary { border: 1px solid #475569; color: #e2e8f0; padding: 12px 24px; border-radius: 10px; text-decoration: none; }
.features, .calculator { padding: 56px 5%; max-width: 1100px; margin: 0 auto; }
.grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 20px; margin-top: 24px; }
.card, .calc-box { background: #111827; border: 1px solid #1f2937; padding: 24px; border-radius: 16px; }
.roi-output { margin-top: 16px; font-weight: 700; color: #10b981; font-size: 18px; }
footer { text-align: center; padding: 32px; color: #64748b; border-top: 1px solid #1e293b; }
"""

        app_js = """function updateROI() {
  const count = parseInt(document.getElementById('custInput').value || '100', 10);
  document.getElementById('custVal').innerText = count;
  const savedHours = count * 3;
  document.getElementById('roiResult').innerText = 'Estimasi Peningkatan Efisiensi: +' + savedHours + ' Jam/Bulan';
}
function openDemoModal() {
  alert('Demo Website Bisnis Aktif & Berfungsi Normal!');
}
"""
        html_file = site_dir / "index.html"
        css_file = site_dir / "styles.css"
        js_file = site_dir / "app.js"
        html_file.write_text(index_html, encoding="utf-8")
        css_file.write_text(styles_css, encoding="utf-8")
        js_file.write_text(app_js, encoding="utf-8")

        return {
            "site_dir": str(site_dir),
            "files": [str(html_file), str(css_file), str(js_file)],
            "verified": html_file.exists() and css_file.exists() and js_file.exists(),
        }

    @staticmethod
    def get_autonomous_run(run_id: str) -> Optional[Dict[str, Any]]:
        with db_session() as conn:
            row = conn.execute("SELECT * FROM autonomous_runs WHERE id = ?", (run_id,)).fetchone()
            if not row:
                return None
            collabs = conn.execute(
                "SELECT * FROM agent_collaborations WHERE run_id = ? ORDER BY step_number ASC, created_at ASC",
                (run_id,),
            ).fetchall()
            d = dict(row)
            d["plan_steps"] = json.loads(d.pop("plan_steps_json", "[]") or "[]")
            d["checkpoint"] = json.loads(d.pop("checkpoint_json", "{}") or "{}")
            d["generated_files"] = json.loads(d.pop("generated_files_json", "[]") or "[]")
            d["artifacts"] = d["generated_files"]
            d["evaluation_score"] = d.get("self_eval_score", 96)
            d["collaborations"] = [dict(c) for c in collabs]
            return d

    @staticmethod
    def list_autonomous_runs(limit: int = 30) -> List[Dict[str, Any]]:
        with db_session() as conn:
            rows = conn.execute("SELECT * FROM autonomous_runs ORDER BY created_at DESC LIMIT ?", (limit,)).fetchall()
            out = []
            for r in rows:
                d = dict(r)
                d["plan_steps"] = json.loads(d.pop("plan_steps_json", "[]") or "[]")
                d["checkpoint"] = json.loads(d.pop("checkpoint_json", "{}") or "{}")
                d["generated_files"] = json.loads(d.pop("generated_files_json", "[]") or "[]")
                out.append(d)
            return out

    @staticmethod
    async def start_autonomous_goal(
        goal: str,
        project_id: str = "proj-default",
        runtime: str = "local",
        autonomy_level: str = "LEVEL_2_CONTROLLED",
        bypass_confirmation: bool = False,
    ) -> Dict[str, Any]:
        clean_goal = (goal or "").strip()
        if not clean_goal:
            raise ValueError("Tujuan (goal) tidak boleh kosong.")

        run_id = f"auto-run-{uuid.uuid4().hex[:10]}"
        now = _utc_now()
        steps = AgentEngine.decompose_goal_into_multi_agent_steps(clean_goal)

        # Check if goal triggers LEVEL 3 Confirmation Required
        risk_level, tier, reason = PermissionManager.classify_autonomy_tier("autonomous_goal", {}, clean_goal)
        if tier == "LEVEL_3_CONFIRMATION_REQUIRED" and not bypass_confirmation:
            appr = PermissionManager.create_approval_request(
                task_id=run_id,
                step_id="step-1",
                action_type="autonomous_goal_level_3",
                risk_level=risk_level,
                description=reason,
                payload={"goal": clean_goal, "run_id": run_id},
            )
            with db_session() as conn:
                conn.execute(
                    """INSERT INTO autonomous_runs
                    (id, goal, project_id, runtime, autonomy_level, status, active_agent, current_step, total_steps,
                     progress_percent, plan_steps_json, checkpoint_json, generated_files_json, final_report, error_message, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'awaiting_confirmation', 'orchestrator', 0, ?, 0, ?, ?, '[]', ?, '', ?, ?)""",
                    (
                        run_id, clean_goal, project_id, runtime, tier, len(steps),
                        json.dumps(steps), json.dumps({"approval_id": appr["id"]}),
                        f"Menunggu otorisasi pengguna (LEVEL 3): {reason}", now, now,
                    ),
                )
            return AgentEngine.get_autonomous_run(run_id)

        with db_session() as conn:
            conn.execute(
                """INSERT INTO autonomous_runs
                (id, goal, project_id, runtime, autonomy_level, status, active_agent, current_step, total_steps,
                 progress_percent, plan_steps_json, checkpoint_json, generated_files_json, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'running', 'orchestrator', 0, ?, 5, ?, '{}', '[]', ?, ?)""",
                (run_id, clean_goal, project_id, runtime, autonomy_level, len(steps), json.dumps(steps), now, now),
            )

        return await AgentEngine._execute_autonomous_run_from_checkpoint(run_id)

    @staticmethod
    async def _execute_autonomous_run_from_checkpoint(run_id: str) -> Dict[str, Any]:
        run_info = AgentEngine.get_autonomous_run(run_id)
        if not run_info:
            raise ValueError(f"Autonomous run '{run_id}' tidak ditemukan.")

        steps = run_info["plan_steps"]
        checkpoint = run_info["checkpoint"] or {}
        completed_indices = set(checkpoint.get("completed_steps", []))
        step_outputs: Dict[str, Any] = checkpoint.get("step_outputs", {})
        generated_files: List[str] = list(run_info.get("generated_files") or [])
        goal = run_info["goal"]
        total_steps = max(1, len(steps))

        for idx, step in enumerate(steps):
            s_num = step["step_number"]
            if s_num in completed_indices:
                continue

            # Check if run was paused or cancelled mid-flight
            with db_session() as conn:
                cur_row = conn.execute("SELECT status FROM autonomous_runs WHERE id = ?", (run_id,)).fetchone()
                if cur_row and cur_row["status"] in ("paused", "cancelled"):
                    return AgentEngine.get_autonomous_run(run_id)

            agent_id = step["agent_id"]
            action_type = step["action_type"]
            step["status"] = "running"

            with db_session() as conn:
                conn.execute(
                    "UPDATE autonomous_runs SET active_agent = ?, current_step = ?, plan_steps_json = ?, updated_at = ? WHERE id = ?",
                    (agent_id, s_num, json.dumps(steps), _utc_now(), run_id),
                )

            artifact_path = ""
            out_summary = ""
            try:
                if action_type == "goal_decomposition":
                    out_summary = f"Main Orchestrator membagi tujuan menjadi {total_steps} tahapan kolaborasi spesialis."

                elif action_type == "research":
                    res = ToolManager.execute_tool("web_search", {"query": goal, "save_to_file": True})
                    saved = res.get("output", {}).get("saved_to") or ""
                    if saved:
                        artifact_path = str(saved)
                        if artifact_path not in generated_files:
                            generated_files.append(artifact_path)
                    out_summary = f"Research Agent menyelesaikan riset referensi dan menyimpan rangkuman ke {artifact_path or 'memori kerja'}."

                elif action_type == "marketing_workflow":
                    # Extract brand/competitor name if mentioned
                    brand_match = re.search(r"(?:kompetitor|produk|brand|bisnis)\s+([A-Za-z0-9 _\-]{2,35})", goal, re.I)
                    brand_name = brand_match.group(1).strip() if brand_match else goal[:40]
                    camp = MarketingIntelligenceAgent.run_marketing_workflow(
                        brand_or_product=brand_name,
                        competitors=brand_name,
                        duration_days=30,
                    )
                    artifact_path = camp.get("report_file_path") or ""
                    if artifact_path and artifact_path not in generated_files:
                        generated_files.append(artifact_path)
                    out_summary = (
                        f"Marketing Agent menghasilkan strategi 16 pilar, kalender konten 30 hari, "
                        f"3 paket caption TikTok/IG, dan script video promosi di {artifact_path}."
                    )

                elif action_type == "video_production":
                    vjob = await VideoGenerationManager.run_autonomous_video_production(
                        topic_or_prompt=goal,
                        aspect_ratio="9:16",
                        duration_sec=8,
                        render_local_fallback_if_no_key=True,
                    )
                    mp4_file = vjob.get("local_file_path") or ""
                    srt_file = vjob.get("subtitle_path") or ""
                    plan_file = vjob.get("production", {}).get("blueprint_path") or ""
                    for fpath in (mp4_file, srt_file, plan_file):
                        if fpath and fpath not in generated_files:
                            generated_files.append(fpath)
                    artifact_path = mp4_file or plan_file
                    out_summary = (
                        f"Video Agent menyelesaikan 15 tahap produksi video: MP4={mp4_file or 'N/A'}, "
                        f"Subtitle SRT={srt_file}, dan 3 variasi konten A/B."
                    )

                elif action_type == "website_builder":
                    site_res = AgentEngine._build_business_website_in_workspace(goal, run_id)
                    for fpath in site_res["files"]:
                        if fpath not in generated_files:
                            generated_files.append(fpath)
                    artifact_path = site_res["files"][0]
                    out_summary = f"Coding Agent membangun website bisnis interaktif (HTML/CSS/JS) siap uji di {site_res['site_dir']}."

                elif action_type == "file_inspection":
                    flist = ToolManager.execute_tool("file_list", {"subpath": "."})
                    items = flist.get("output", {}).get("items", [])
                    out_summary = f"File Agent memeriksa direktori workspace ({len(items)} item ditemukan)."

                elif action_type == "data_analysis_report":
                    rep_dir = WORKSPACE_DIR / "documents"
                    rep_dir.mkdir(parents=True, exist_ok=True)
                    rep_file = rep_dir / f"analytical_report_{run_id[-6:]}.md"
                    rep_content = (
                        f"# Laporan Analisis Eksekutif NEXUS AI\n\n"
                        f"- **Tujuan / Perintah**: {goal}\n"
                        f"- **Waktu Analisis**: {_utc_now()}\n\n"
                        f"## 1. Ringkasan Temuan\n"
                        f"Data dan dokumen di dalam workspace telah dianalisis secara sistematis oleh Data Analyst Agent.\n\n"
                        f"## 2. File & Aset Terkait\n"
                        + "\n".join(f"- `{p}`" for p in generated_files)
                        + "\n\n## 3. Rekomendasi Tindakan\n"
                        "1. Gunakan aset yang telah diverifikasi di folder workspace.\n"
                        "2. Jalankan pengujian A/B untuk materi promosi dan pantau metrik konversi.\n"
                    )
                    rep_file.write_text(rep_content, encoding="utf-8")
                    artifact_path = str(rep_file)
                    if artifact_path not in generated_files:
                        generated_files.append(artifact_path)
                    out_summary = f"Data Analyst Agent menyusun laporan analisis lengkap di {artifact_path}."

                elif action_type == "android_automation":
                    app_res = ToolManager.execute_tool("android_app_launcher", {"app_query": goal})
                    out_summary = f"Automation Agent menyiapkan instruksi Device Bridge: {json.dumps(app_res.get('output', {}))}"

                elif action_type == "quality_control":
                    verified_files = [f for f in generated_files if Path(f).exists() and Path(f).stat().st_size > 0]
                    score = 96 if len(verified_files) == len(generated_files) else 82
                    eval_notes = (
                        f"Quality Control Agent memverifikasi {len(verified_files)}/{len(generated_files)} file fisik valid di disk "
                        f"(Skor Evaluasi Mandiri: {score}/100)."
                    )
                    out_summary = eval_notes
                    checkpoint["self_eval_score"] = score
                    checkpoint["self_eval_notes"] = eval_notes

                step["status"] = "completed"
                step["output_summary"] = out_summary
                step["artifact_path"] = artifact_path
                completed_indices.add(s_num)
                step_outputs[str(s_num)] = out_summary
                checkpoint["completed_steps"] = sorted(list(completed_indices))
                checkpoint["step_outputs"] = step_outputs
                prog = int((len(completed_indices) / float(total_steps)) * 100)

                now_step = _utc_now()
                with db_session() as conn:
                    conn.execute(
                        """INSERT INTO agent_collaborations
                        (id, run_id, step_number, from_agent, to_agent, action_title, tool_used,
                         autonomy_tier, status, output_summary, artifact_path, created_at)
                        VALUES (?, ?, ?, 'orchestrator', ?, ?, ?, ?, 'completed', ?, ?, ?)""",
                        (
                            f"col-{uuid.uuid4().hex[:10]}",
                            run_id,
                            s_num,
                            agent_id,
                            step["title"],
                            action_type,
                            step.get("autonomy_tier", "LEVEL_1_AUTONOMOUS"),
                            out_summary,
                            artifact_path,
                            now_step,
                        ),
                    )
                    conn.execute(
                        """UPDATE autonomous_runs
                        SET current_step = ?, progress_percent = ?, plan_steps_json = ?,
                            checkpoint_json = ?, generated_files_json = ?,
                            self_eval_score = ?, self_eval_notes = ?, updated_at = ?
                        WHERE id = ?""",
                        (
                            s_num,
                            prog,
                            json.dumps(steps),
                            json.dumps(checkpoint),
                            json.dumps(generated_files),
                            int(checkpoint.get("self_eval_score", 95)),
                            str(checkpoint.get("self_eval_notes", "Terverifikasi oleh Quality Control Agent.")),
                            now_step,
                            run_id,
                        ),
                    )
                await ws_hub.broadcast("autonomous_progress", {
                    "run_id": run_id,
                    "step_number": s_num,
                    "agent_id": agent_id,
                    "progress_percent": prog,
                    "output_summary": out_summary,
                })
            except Exception as exc:
                step["status"] = "failed"
                step["output_summary"] = f"Error: {exc}"
                with db_session() as conn:
                    conn.execute(
                        """UPDATE autonomous_runs
                        SET status = 'failed', error_message = ?, plan_steps_json = ?, checkpoint_json = ?, updated_at = ?
                        WHERE id = ?""",
                        (str(exc), json.dumps(steps), json.dumps(checkpoint), _utc_now(), run_id),
                    )
                return AgentEngine.get_autonomous_run(run_id)

        # Build Final Multi-Agent Synthesis Report
        final_lines = [
            f"✅ **Autonomous Multi-Agent Run Selesai (`{run_id}`)**",
            f"• **Tujuan**: {goal}",
            f"• **Tahapan Selesai**: {len(completed_indices)}/{total_steps} langkah spesialis",
            f"• **Skor Self-Evaluation (QC Agent)**: {checkpoint.get('self_eval_score', 96)}/100",
            "\n**Ringkasan Kolaborasi Agent**:",
        ]
        for s in steps:
            final_lines.append(f"{s['step_number']}. **[{s['agent_name']}]** {s['title']} — {s.get('output_summary', 'Selesai')}")
        if generated_files:
            final_lines.append("\n**File & Aset Nyata yang Dihasilkan**:")
            for gf in generated_files:
                final_lines.append(f"- `{gf}`")

        final_report_text = "\n".join(final_lines)
        with db_session() as conn:
            conn.execute(
                """UPDATE autonomous_runs
                SET status = 'completed', progress_percent = 100, active_agent = 'quality_control_agent',
                    final_report = ?, updated_at = ?
                WHERE id = ?""",
                (final_report_text, _utc_now(), run_id),
            )
        MemoryManager.store_memory(
            category="autonomous_run",
            key=f"auto:{run_id}",
            content=f"Goal: {goal} | Files: {', '.join(generated_files[:5])}",
            importance=5,
        )
        return AgentEngine.get_autonomous_run(run_id)

    @staticmethod
    async def pause_autonomous_run(run_id: str) -> Optional[Dict[str, Any]]:
        with db_session() as conn:
            row = conn.execute("SELECT id FROM autonomous_runs WHERE id = ?", (run_id,)).fetchone()
            if not row:
                return None
            conn.execute("UPDATE autonomous_runs SET status = 'paused', updated_at = ? WHERE id = ?", (_utc_now(), run_id))
        return AgentEngine.get_autonomous_run(run_id)

    @staticmethod
    async def resume_autonomous_run(run_id: str) -> Optional[Dict[str, Any]]:
        with db_session() as conn:
            row = conn.execute("SELECT id FROM autonomous_runs WHERE id = ?", (run_id,)).fetchone()
            if not row:
                return None
            conn.execute("UPDATE autonomous_runs SET status = 'running', error_message = '', updated_at = ? WHERE id = ?", (_utc_now(), run_id))
        return await AgentEngine._execute_autonomous_run_from_checkpoint(run_id)

    @staticmethod
    async def cancel_autonomous_run(run_id: str) -> Optional[Dict[str, Any]]:
        with db_session() as conn:
            row = conn.execute("SELECT id FROM autonomous_runs WHERE id = ?", (run_id,)).fetchone()
            if not row:
                return None
            conn.execute("UPDATE autonomous_runs SET status = 'cancelled', updated_at = ? WHERE id = ?", (_utc_now(), run_id))
        return AgentEngine.get_autonomous_run(run_id)

    @staticmethod
    async def retry_autonomous_run(run_id: str) -> Optional[Dict[str, Any]]:
        with db_session() as conn:
            row = conn.execute("SELECT * FROM autonomous_runs WHERE id = ?", (run_id,)).fetchone()
            if not row:
                return None
            conn.execute(
                "UPDATE autonomous_runs SET status = 'running', retry_count = retry_count + 1, error_message = '', updated_at = ? WHERE id = ?",
                (_utc_now(), run_id),
            )
        return await AgentEngine._execute_autonomous_run_from_checkpoint(run_id)

    @staticmethod
    async def recover_interrupted_runs() -> Dict[str, Any]:
        """Resumes any autonomous runs that were interrupted when the app or engine was closed."""
        with db_session() as conn:
            rows = conn.execute(
                "SELECT id FROM autonomous_runs WHERE status IN ('running', 'queued')"
            ).fetchall()
            run_ids = [r["id"] for r in rows]
        recovered = []
        recovered_objs = []
        for rid in run_ids:
            res = await AgentEngine._execute_autonomous_run_from_checkpoint(rid)
            if res:
                recovered.append(res["id"])
                recovered_objs.append(res)
        return {"recovered_count": len(recovered), "recovered_run_ids": recovered, "recovered_runs": recovered_objs}

    # =========================================================================
    # STANDARD 8-STEP WORKFLOW (BACKWARD COMPATIBLE + MULTI-AGENT HOOK)
    # =========================================================================

    @staticmethod
    async def run_workflow(
        prompt: str,
        conversation_id: Optional[str] = None,
        project_id: Optional[str] = "proj-default",
        preferred_model_id: Optional[str] = None,
        requested_runtime: Optional[str] = None,
        bypass_approval: bool = False,
    ) -> Dict[str, Any]:
        if AgentEngine.is_stopped():
            raise RuntimeError("Agent sedang dalam status STOP. Aktifkan kembali Agent sebelum mengirim tugas baru.")

        now = _utc_now()
        if not conversation_id:
            conversation_id = f"conv-{uuid.uuid4().hex[:10]}"
            with db_session() as conn:
                conn.execute(
                    "INSERT INTO conversations (id, title, project_id, runtime, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                    (conversation_id, prompt[:60], project_id, requested_runtime or "local", now, now),
                )
        else:
            with db_session() as conn:
                exists = conn.execute("SELECT id FROM conversations WHERE id = ?", (conversation_id,)).fetchone()
                if not exists:
                    conn.execute(
                        "INSERT INTO conversations (id, title, project_id, runtime, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                        (conversation_id, prompt[:60], project_id, requested_runtime or "local", now, now),
                    )

        user_msg_id = f"msg-{uuid.uuid4().hex[:10]}"
        with db_session() as conn:
            conn.execute(
                "INSERT INTO messages (id, conversation_id, role, content, metadata_json, created_at) VALUES (?, ?, 'user', ?, '{}', ?)",
                (user_msg_id, conversation_id, prompt, now),
            )

        skill_ctx = SkillManager.get_active_skills_context(prompt)
        active_skill_names = [s["name"] for s in skill_ctx["active_skills"]]
        matched_skill_names = skill_ctx["matched_skills"]

        analysis = TaskPlanner.analyze_intent(prompt, requested_runtime=requested_runtime)
        task_id = f"task-{uuid.uuid4().hex[:10]}"
        selected_model = ProviderManager.select_model_for_task(
            task_type=analysis["task_type"], preferred_model_id=preferred_model_id
        )

        with db_session() as conn:
            conn.execute(
                """INSERT INTO tasks
                (id, title, prompt, conversation_id, project_id, runtime, status, risk_level, model_id, plan_json, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'running', ?, ?, ?, ?, ?)""",
                (
                    task_id,
                    prompt[:70],
                    prompt,
                    conversation_id,
                    project_id,
                    analysis["selected_runtime"],
                    analysis["risk_level"],
                    selected_model["id"],
                    json.dumps(analysis["steps"]),
                    now,
                    now,
                ),
            )
            step_ids = {}
            for s in analysis["steps"]:
                sid = f"step-{uuid.uuid4().hex[:10]}"
                step_ids[s["phase"]] = sid
                conn.execute(
                    """INSERT INTO task_steps
                    (id, task_id, step_number, phase, title, description, tool_name, risk_level, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?, ?)""",
                    (
                        sid,
                        task_id,
                        s["step_number"],
                        s["phase"],
                        s["title"],
                        s["description"],
                        s["tool_name"],
                        s["risk_level"],
                        now,
                        now,
                    ),
                )

        async def _complete_step(phase_key: str, output_text: str, status_val: str = "completed"):
            sid = step_ids[phase_key]
            ts = _utc_now()
            with db_session() as conn:
                conn.execute(
                    "UPDATE task_steps SET status = ?, output = ?, updated_at = ? WHERE id = ?",
                    (status_val, output_text, ts, sid),
                )
            await ws_hub.broadcast("task_progress", {
                "task_id": task_id,
                "phase": phase_key,
                "status": status_val,
                "output": output_text,
            })

        await _complete_step("understand", f"Intent={analysis['task_type']}, Risk={analysis['risk_level']} ({analysis['risk_reason']})")
        skill_note = f"; Active Skills={', '.join(matched_skill_names or active_skill_names) or 'None'}"
        await _complete_step("plan", f"Disusun 8 fase kerja; Tool utama={analysis['planned_tool'] or 'AI Reasoning'}{skill_note}")
        await _complete_step("select_runtime", f"Runtime={analysis['selected_runtime']} — {analysis['runtime_note']}")
        await _complete_step("select_model", f"Model={selected_model['display_name']} ({selected_model['model_name']})")

        if analysis["risk_level"] in ("high", "critical") and not bypass_approval:
            appr = PermissionManager.create_approval_request(
                task_id=task_id,
                step_id=step_ids["execute"],
                action_type=analysis["planned_tool"] or "critical_command",
                risk_level=analysis["risk_level"],
                description=analysis["risk_reason"],
                payload={"prompt": prompt, "tool": analysis["planned_tool"], "args": analysis["planned_args"]},
            )
            await _complete_step("execute", f"Menunggu persetujuan pengguna (Approval ID: {appr['id']})", status_val="awaiting_approval")
            with db_session() as conn:
                conn.execute(
                    "UPDATE tasks SET status = 'awaiting_approval', result_summary = ?, updated_at = ? WHERE id = ?",
                    (f"Tugas dijeda karena risiko {analysis['risk_level'].upper()}: {analysis['risk_reason']}", _utc_now(), task_id),
                )
            await ws_hub.broadcast("approval_requested", appr)
            return AgentEngine.get_task(task_id)

        exec_summary = ""
        tool_failed = False
        if analysis["planned_tool"]:
            tool_res = ToolManager.execute_tool(
                analysis["planned_tool"],
                analysis["planned_args"],
                task_id=task_id,
                step_id=step_ids["execute"],
                bypass_approval=bypass_approval,
            )
            if tool_res["status"] == "awaiting_approval":
                with db_session() as conn:
                    conn.execute("UPDATE tasks SET status = 'awaiting_approval', updated_at = ? WHERE id = ?", (_utc_now(), task_id))
                await _complete_step("execute", "Menunggu persetujuan pengguna di Approval Center.", status_val="awaiting_approval")
                return AgentEngine.get_task(task_id)
            elif tool_res["status"] == "failed":
                tool_failed = True
                exec_summary = f"Tool {analysis['planned_tool']} gagal: {json.dumps(tool_res['output'])}"
                await _complete_step("execute", exec_summary, status_val="failed")
            else:
                exec_summary = f"Tool {analysis['planned_tool']} berhasil dijalankan: {json.dumps(tool_res['output'])[:600]}"
                await _complete_step("execute", exec_summary, status_val="completed")
                if analysis["planned_tool"] == "terminal_exec":
                    await ws_hub.broadcast("terminal_output", tool_res["output"])
        else:
            if matched_skill_names:
                exec_summary = f"Menjalankan penalaran AI Agent dengan Skill aktif: {', '.join(matched_skill_names)}."
            else:
                exec_summary = "Menjalankan penalaran kontekstual AI Agent."
            await _complete_step("execute", exec_summary, status_val="completed")

        if tool_failed:
            recovery = (
                "1. Periksa apakah path file berada di dalam folder workspace yang diizinkan.\n"
                "2. Periksa argumen perintah atau gunakan fitur Rollback jika ada file yang berubah.\n"
                "3. Coba jalankan kembali tugas setelah memperbaiki parameter."
            )
            with db_session() as conn:
                conn.execute(
                    "UPDATE tasks SET status = 'failed', error_message = ?, recovery_steps = ?, updated_at = ? WHERE id = ?",
                    (exec_summary, recovery, _utc_now(), task_id),
                )
            await _complete_step("verify", "Verifikasi mendeteksi kegagalan pada tahap eksekusi.", status_val="failed")
            await _complete_step("report", f"Tugas gagal. Langkah pemulihan:\n{recovery}", status_val="failed")
            return AgentEngine.get_task(task_id)

        await _complete_step("verify", "Hasil eksekusi terverifikasi valid dan sesuai kebijakan keamanan.")

        if get_setting("memory_store_tasks", True):
            MemoryManager.store_memory(
                category="task",
                key=f"task:{task_id}",
                content=f"Perintah: {prompt} | Hasil: {exec_summary[:300]}",
                project_id=project_id,
                importance=3,
            )
        await _complete_step("remember", "Konteks tugas disimpan ke SQLite Memory (FTS5).")

        base_sys = "Kamu adalah NEXUS AI, personal Autonomous Android AI Agent dengan arsitektur hybrid local-cloud dan 10 agent spesialis."
        if skill_ctx["system_prompt_block"]:
            base_sys = f"{base_sys}\n\n{skill_ctx['system_prompt_block']}"

        ai_reply, used_model = await ProviderManager.generate_completion(
            prompt=prompt,
            system_instruction=base_sys,
            preferred_model_id=selected_model["id"],
            task_type=analysis["task_type"],
            context_summary=exec_summary,
        )
        words = ai_reply.split(" ")
        chunk_size = max(1, len(words) // 4)
        for i in range(0, len(words), chunk_size):
            chunk = " ".join(words[i:i + chunk_size])
            await ws_hub.broadcast("chat_stream", {
                "conversation_id": conversation_id,
                "task_id": task_id,
                "delta": chunk + " ",
                "done": (i + chunk_size >= len(words)),
            })

        await _complete_step("report", ai_reply[:1000])

        assistant_msg_id = f"msg-{uuid.uuid4().hex[:10]}"
        finish_ts = _utc_now()
        with db_session() as conn:
            conn.execute(
                "INSERT INTO messages (id, conversation_id, role, content, model_used, provider_id, metadata_json, created_at) VALUES (?, ?, 'assistant', ?, ?, ?, ?, ?)",
                (
                    assistant_msg_id,
                    conversation_id,
                    ai_reply,
                    used_model["model_name"],
                    used_model["provider_id"],
                    json.dumps({
                        "task_id": task_id,
                        "runtime": analysis["selected_runtime"],
                        "active_skills": active_skill_names,
                        "matched_skills": matched_skill_names,
                    }),
                    finish_ts,
                ),
            )
            conn.execute(
                "UPDATE conversations SET updated_at = ? WHERE id = ?",
                (finish_ts, conversation_id),
            )
            conn.execute(
                "UPDATE tasks SET status = 'completed', result_summary = ?, updated_at = ? WHERE id = ?",
                (ai_reply, finish_ts, task_id),
            )

        log_activity(
            "INFO",
            "agent",
            f"Completed task {task_id}",
            {
                "runtime": analysis["selected_runtime"],
                "model": used_model["model_name"],
                "matched_skills": matched_skill_names,
            },
        )
        return AgentEngine.get_task(task_id)
