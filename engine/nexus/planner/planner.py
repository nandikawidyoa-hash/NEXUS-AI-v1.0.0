import re
from typing import Any, Dict, List, Optional
from nexus.database.db import get_setting
from nexus.permissions.manager import PermissionManager

WORKFLOW_PHASES = [
    ("understand", "1. Understand — Menganalisis maksud perintah, entitas, dan batasan keamanan"),
    ("plan", "2. Plan — Menyusun rencana langkah kerja dan tool yang dibutuhkan"),
    ("select_runtime", "3. Select Runtime — Memilih Local Runtime (Termux) atau Cloud Runtime"),
    ("select_model", "4. Select Model — Menentukan model AI optimal dengan dukungan fallback"),
    ("execute", "5. Execute — Menjalankan tool/tindakan sesuai rencana dan izin"),
    ("verify", "6. Verify — Memverifikasi hasil eksekusi dan integritas output"),
    ("remember", "7. Remember — Menyimpan konteks relevan ke SQLite Memory (FTS5)"),
    ("report", "8. Report — Melaporkan hasil akhir dan langkah pemulihan jika ada kendala"),
]

LOCAL_ONLY_KEYWORDS = [
    "file", "folder", "dokumen", "workspace", "backup", "terminal", "shell",
    "baterai", "battery", "kamera", "camera", "clipboard", "intent", "aplikasi", "apk",
    "buka ", "tiktok", "whatsapp", "chrome", "youtube", "instagram", "telegram", "termux",
]

APP_LAUNCH_REGEX = re.compile(
    r"^\s*(?:tolong\s+|coba\s+|nexus\s+)?(?:buka|jalankan|open|launch)\s+(?:aplikasi\s+|app\s+)?([a-zA-Z0-9_.\-]+)(?:\s+(?:dan|lalu|untuk)\s+(?:cari|search|buka)\s+(.+))?\s*$",
    re.IGNORECASE,
)


class TaskPlanner:
    @staticmethod
    def analyze_intent(prompt: str, requested_runtime: Optional[str] = None) -> Dict[str, Any]:
        lower = prompt.lower().strip()
        requires_local_device = any(k in lower for k in LOCAL_ONLY_KEYWORDS)

        task_type = "chat"
        planned_tool: Optional[str] = None
        planned_args: Dict[str, Any] = {}

        app_match = APP_LAUNCH_REGEX.match(prompt.strip())
        if app_match and app_match.group(1).lower() not in ("file", "folder", "dokumen", "catatan"):
            app_target = app_match.group(1).strip()
            search_query = (app_match.group(2) or "").strip()
            task_type = "android_automation"
            planned_tool = "android_app_launcher"
            planned_args = {
                "app_query": app_target,
                "search_query": search_query,
            }
            requires_local_device = True
        elif any(w in lower for w in ("buat file", "tulis file", "simpan catatan", "write file")):
            task_type = "file_management"
            planned_tool = "file_write"
            match_name = re.search(r"([a-zA-Z0-9_\-]+\.(?:txt|md|py|json))", prompt)
            fname = match_name.group(1) if match_name else "documents/catatan_nexus.md"
            if "/" not in fname:
                fname = f"documents/{fname}"
            planned_args = {"path": fname, "content": f"# Dokumen NEXUS AI\n\nDibuat dari perintah: {prompt}\n"}
        elif any(w in lower for w in ("organisasi folder", "rapikan folder", "organize")):
            task_type = "file_management"
            planned_tool = "file_organize"
            planned_args = {"subpath": "downloads"}
        elif any(w in lower for w in ("cari file", "search file", "daftar file", "list file")):
            task_type = "file_management"
            planned_tool = "file_list"
            planned_args = {"subpath": "."}
        elif any(w in lower for w in ("hapus semua", "delete all", "rm -rf")):
            task_type = "file_management"
            planned_tool = "file_delete"
            planned_args = {"paths": ["documents/temp1.txt", "documents/temp2.txt"], "recursive": True}
        elif any(w in lower for w in ("terminal", "jalankan perintah", "shell:", "cmd:")):
            task_type = "terminal"
            planned_tool = "terminal_exec"
            cmd_match = re.search(r"(?:shell:|cmd:|perintah)\s*(.+)$", prompt, re.IGNORECASE)
            cmd = cmd_match.group(1).strip() if cmd_match else "echo 'NEXUS Local Terminal Ready' && uname -a"
            planned_args = {"command": cmd, "timeout_sec": 10}
        elif any(w in lower for w in ("riset", "cari di web", "search web", "bandingkan sumber", "research")):
            task_type = "research"
            planned_tool = "web_search"
            planned_args = {"query": prompt, "save_to_file": True}
        elif any(w in lower for w in ("cek kode", "inspect code", "periksa bug", "analisis kode", "coding")):
            task_type = "coding"
            planned_tool = "code_inspect"
            planned_args = {"path": "projects"}

        risk_level, risk_reason = PermissionManager.classify_action_risk(
            planned_tool or "chat", planned_args, prompt_text=prompt
        )

        default_rt = requested_runtime or get_setting("active_runtime", "local")
        selected_runtime = default_rt
        runtime_note = "Local Runtime (Termux / Android Device Bridge) aktif dan dipilih sebagai eksekutor utama."
        if selected_runtime == "cloud" and requires_local_device:
            runtime_note = (
                "PERINGATAN ARSITEKTUR: Tugas ini membutuhkan akses file/aplikasi lokal HP. "
                "Cloud Runtime TIDAK BISA mengakses file atau aplikasi lokal HP saat HP offline. "
                "Dialihkan ke Local Runtime."
            )
            selected_runtime = "local"

        steps = []
        for idx, (phase_key, phase_title) in enumerate(WORKFLOW_PHASES, 1):
            step_risk = risk_level if phase_key == "execute" else "low"
            step_tool = planned_tool if phase_key == "execute" else None
            steps.append({
                "step_number": idx,
                "phase": phase_key,
                "title": phase_title,
                "description": TaskPlanner._describe_phase(phase_key, prompt, selected_runtime, planned_tool, risk_reason),
                "tool_name": step_tool,
                "tool_args": planned_args if phase_key == "execute" else {},
                "risk_level": step_risk,
            })

        return {
            "task_type": task_type,
            "requires_local_device": requires_local_device,
            "selected_runtime": selected_runtime,
            "runtime_note": runtime_note,
            "risk_level": risk_level,
            "risk_reason": risk_reason,
            "planned_tool": planned_tool,
            "planned_args": planned_args,
            "steps": steps,
        }

    @staticmethod
    def _describe_phase(phase: str, prompt: str, runtime: str, tool: Optional[str], risk_reason: str) -> str:
        if phase == "understand":
            return f"Memahami perintah: '{prompt[:80]}' beserta analisis risiko ({risk_reason})."
        if phase == "plan":
            return f"Menyiapkan langkah eksekusi {'menggunakan tool ' + tool if tool else 'berbasis penalaran AI'}."
        if phase == "select_runtime":
            return f"Memilih runtime '{runtime}' sesuai ketersediaan akses perangkat & koneksi."
        if phase == "select_model":
            return "Memilih model terbaik via ProviderManager dengan fallback otomatis."
        if phase == "execute":
            return f"Menjalankan {'tool ' + tool if tool else 'sintesis jawaban AI'} dengan pengawasan izin."
        if phase == "verify":
            return "Memeriksa kelengkapan output, kode keluar (exit code), dan validitas hasil."
        if phase == "remember":
            return "Menyimpan ringkasan tugas dan fakta penting ke SQLite FTS5 Memory."
        return "Menyusun laporan akhir yang transparan kepada pengguna."
