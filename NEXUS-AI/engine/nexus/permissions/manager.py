import json
import uuid
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
from nexus.config import WORKSPACE_DIR
from nexus.database.db import _utc_now, db_session, get_setting, log_activity, set_setting

RISK_LEVELS = ("low", "medium", "high", "critical")

AUTONOMY_TIERS = {
    "LEVEL_1_AUTONOMOUS": (
        "Riset, analisis, perencanaan, pembuatan draft, strategi marketing, produksi storyboard/video lokal, "
        "coding dalam workspace yang diizinkan, dan pengelolaan file dalam direktori yang disetujui."
    ),
    "LEVEL_2_CONTROLLED": (
        "Menjalankan aplikasi Android, mengubah konfigurasi, menginstal skill/dependensi, dan tindakan "
        "non-destruktif yang berdampak pada perangkat atau project."
    ),
    "LEVEL_3_CONFIRMATION_REQUIRED": (
        "Pembayaran, pembelian, pengiriman pesan eksternal, publikasi konten publik, penghapusan data permanen/massal, "
        "pengiriman email, dan tindakan yang tidak dapat dibatalkan."
    ),
}

CRITICAL_KEYWORDS = [
    "transfer bank", "bayar tagihan", "checkout", "payment", "send money",
    "crypto transfer", "purchase", "beli otomatis", "kartu kredit", "pin atm",
    "kirim email ke semua", "publish ke publik", "posting otomatis ke akun",
]

HIGH_RISK_COMMANDS = [
    "rm -rf", "mkfs", "dd if=", "chmod 777", "chown", "su ", "sudo ", "reboot", "shutdown",
    "iptables", "format", "del /f",
]


class PermissionManager:
    @staticmethod
    def get_autonomy_config() -> Dict[str, Any]:
        return {
            "autonomous_mode_enabled": bool(get_setting("autonomous_mode_enabled", True)),
            "active_tier": get_setting("autonomy_permission_tier", "LEVEL_2_CONTROLLED"),
            "tiers": AUTONOMY_TIERS,
        }

    @staticmethod
    def set_autonomy_config(autonomous_mode_enabled: bool, active_tier: str = "LEVEL_2_CONTROLLED") -> Dict[str, Any]:
        if active_tier not in AUTONOMY_TIERS:
            active_tier = "LEVEL_2_CONTROLLED"
        set_setting("autonomous_mode_enabled", bool(autonomous_mode_enabled))
        set_setting("autonomy_permission_tier", active_tier)
        log_activity("INFO", "security", f"Updated autonomy config: mode={autonomous_mode_enabled}, tier={active_tier}")
        return PermissionManager.get_autonomy_config()

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
    def classify_autonomy_tier(tool_name: str, args: Dict[str, Any], prompt_text: str = "") -> Tuple[str, str, str]:
        """Returns (risk_level, autonomy_tier, reason)."""
        risk_level, reason = PermissionManager.classify_action_risk(tool_name, args, prompt_text)
        if risk_level in ("high", "critical"):
            return (risk_level, "LEVEL_3_CONFIRMATION_REQUIRED", reason)
        if tool_name in ("android_app_launcher", "terminal_exec") or risk_level == "medium":
            if tool_name in ("file_write", "file_edit", "file_organize", "code_test"):
                return ("low", "LEVEL_1_AUTONOMOUS", f"{reason} (Berjalan mandiri di dalam Workspace yang diizinkan).")
            return (risk_level, "LEVEL_2_CONTROLLED", reason)
        return ("low", "LEVEL_1_AUTONOMOUS", reason)

    @staticmethod
    def classify_action_risk(tool_name: str, args: Dict[str, Any], prompt_text: str = "") -> Tuple[str, str]:
        lower_prompt = (prompt_text or "").lower()
        for kw in CRITICAL_KEYWORDS:
            if kw in lower_prompt:
                return ("critical", f"LEVEL 3 (Confirmation Required): Tindakan sensitif/eksternal terdeteksi ('{kw}'). Wajib konfirmasi pengguna.")

        if tool_name in (
            "file_read", "file_search", "file_list", "file_analyze",
            "web_search", "web_read", "code_inspect", "marketing_workflow",
            "video_production", "website_builder", "data_analysis",
        ):
            return ("low", "LEVEL 1 (Autonomous): Operasi riset, kreasi konten, atau analisis dalam direktori yang diizinkan.")

        if tool_name in ("file_write", "file_edit", "file_move", "file_organize", "code_edit", "code_test"):
            return ("medium", "LEVEL 1 (Autonomous Workspace): Modifikasi file di dalam workspace dengan backup otomatis.")

        if tool_name == "android_app_launcher":
            return ("low", "LEVEL 2 (Controlled): Membuka aplikasi Android melalui Device Bridge resmi.")

        if tool_name == "file_delete":
            paths = args.get("paths", [])
            if isinstance(paths, list) and len(paths) > 1:
                return ("high", f"LEVEL 3 (Confirmation Required): Penghapusan banyak file ({len(paths)} file) membutuhkan persetujuan pengguna.")
            if args.get("recursive"):
                return ("high", "LEVEL 3 (Confirmation Required): Penghapusan folder rekursif membutuhkan persetujuan pengguna.")
            return ("medium", "LEVEL 2 (Controlled): Penghapusan satu file dengan backup otomatis.")

        if tool_name == "terminal_exec":
            cmd = str(args.get("command", "")).strip()
            for bad in HIGH_RISK_COMMANDS:
                if bad in cmd.lower():
                    return ("high", f"LEVEL 3 (Confirmation Required): Perintah terminal berisiko tinggi ('{bad}').")
            return ("medium", "LEVEL 2 (Controlled): Eksekusi perintah terminal standar non-root di dalam sandbox workspace.")

        return ("low", "LEVEL 1 (Autonomous): Operasi standar.")

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
