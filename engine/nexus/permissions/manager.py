import json
import uuid
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
from nexus.config import WORKSPACE_DIR
from nexus.database.db import _utc_now, db_session, get_setting, log_activity

RISK_LEVELS = ("low", "medium", "high", "critical")

CRITICAL_KEYWORDS = [
    "transfer bank", "bayar tagihan", "checkout", "payment", "send money",
    "crypto transfer", "purchase", "beli otomatis", "kartu kredit", "pin atm",
]

HIGH_RISK_COMMANDS = [
    "rm -rf", "mkfs", "dd if=", "chmod 777", "chown", "su ", "sudo ", "reboot", "shutdown",
    "iptables", "format", "del /f",
]

class PermissionManager:
    @staticmethod
    def is_path_allowed(target_path: Path) -> bool:
        resolved = target_path.resolve()
        allowed_dirs = get_setting("allowed_directories", [str(WORKSPACE_DIR)])
        if str(resolved).startswith(str(WORKSPACE_DIR.resolve())):
            return True
        for d in allowed_dirs:
            try:
                if str(resolved).startswith(str(Path(d).resolve())):
                    return True
            except Exception:
                continue
        return False

    @staticmethod
    def classify_action_risk(tool_name: str, args: Dict[str, Any], prompt_text: str = "") -> Tuple[str, str]:
        lower_prompt = (prompt_text or "").lower()
        for kw in CRITICAL_KEYWORDS:
            if kw in lower_prompt:
                return ("critical", f"Tindakan finansial/pembayaran terdeteksi ('{kw}'). Dilarang berjalan otomatis.")

        if tool_name in ("file_read", "file_search", "file_list", "file_analyze", "web_search", "web_read", "code_inspect"):
            return ("low", "Operasi baca atau riset informasi dalam batas direktori yang diizinkan.")

        if tool_name in ("file_write", "file_edit", "file_move", "file_organize", "code_edit", "code_test"):
            return ("medium", "Operasi modifikasi file di dalam workspace; wajib membuat backup otomatis.")

        if tool_name == "file_delete":
            paths = args.get("paths", [])
            if isinstance(paths, list) and len(paths) > 1:
                return ("high", f"Penghapusan banyak file ({len(paths)} file) membutuhkan persetujuan pengguna.")
            if args.get("recursive"):
                return ("high", "Penghapusan folder rekursif membutuhkan persetujuan pengguna.")
            return ("medium", "Penghapusan satu file dengan backup otomatis.")

        if tool_name == "terminal_exec":
            cmd = str(args.get("command", "")).strip()
            for bad in HIGH_RISK_COMMANDS:
                if bad in cmd.lower():
                    return ("high", f"Perintah terminal mengandung operasi berisiko tinggi ('{bad}').")
            return ("medium", "Eksekusi perintah terminal standar non-root di dalam sandbox workspace.")

        return ("low", "Operasi standar.")

    @staticmethod
    def create_approval_request(
        task_id: Optional[str],
        step_id: Optional[str],
        action_type: str,
        risk_level: str,
        description: str,
        payload: Dict[str, Any],
    ) -> Dict[str, Any]:
        app_id = f"appr-{uuid.uuid4().hex[:10]}"
        now = _utc_now()
        with db_session() as conn:
            conn.execute(
                """INSERT INTO approvals
                (id, task_id, step_id, action_type, risk_level, description, payload_json, status, requested_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'pending', ?)""",
                (app_id, task_id, step_id, action_type, risk_level, description, json.dumps(payload), now),
            )
        log_activity("WARNING", "security", f"Approval requested for {action_type} ({risk_level})", {"approval_id": app_id, "task_id": task_id})
        return {
            "id": app_id,
            "task_id": task_id,
            "step_id": step_id,
            "action_type": action_type,
            "risk_level": risk_level,
            "description": description,
            "payload": payload,
            "status": "pending",
            "requested_at": now,
        }

    @staticmethod
    def resolve_approval(approval_id: str, approved: bool, note: str = "") -> Optional[Dict[str, Any]]:
        now = _utc_now()
        new_status = "approved" if approved else "rejected"
        with db_session() as conn:
            row = conn.execute("SELECT * FROM approvals WHERE id = ?", (approval_id,)).fetchone()
            if not row:
                return None
            conn.execute(
                "UPDATE approvals SET status = ?, resolved_at = ?, resolved_by_note = ? WHERE id = ?",
                (new_status, now, note, approval_id),
            )
            if row["task_id"]:
                task_status = "running" if approved else "cancelled"
                conn.execute("UPDATE tasks SET status = ?, updated_at = ? WHERE id = ?", (task_status, now, row["task_id"]))
            updated = conn.execute("SELECT * FROM approvals WHERE id = ?", (approval_id,)).fetchone()
        log_activity("INFO", "security", f"Approval {approval_id} {new_status}", {"note": note})
        return dict(updated) if updated else None
