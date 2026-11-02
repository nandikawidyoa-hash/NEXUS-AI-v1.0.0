import time
import uuid
from typing import Any, Dict, List, Optional
from nexus.agent.engine import AgentEngine
from nexus.database.db import _utc_now, db_session, log_activity

ANDROID_BG_NOTICE = (
    "Catatan Batasan Android: Sistem operasi Android dapat menghentikan proses latar belakang Termux "
    "(Phantom Process Killer / Doze Mode) jika layar mati dalam waktu lama dan tidak ada WakeLock atau "
    "Android Foreground Service yang aktif. Untuk tugas terjadwal yang wajib berjalan saat HP mati/offline, "
    "arahkan runtime ke 'cloud'."
)

class AutomationScheduler:
    @staticmethod
    def list_automations() -> List[Dict[str, Any]]:
        with db_session() as conn:
            rows = conn.execute("SELECT * FROM automations ORDER BY created_at DESC").fetchall()
            out = []
            for r in rows:
                d = dict(r)
                d["enabled"] = bool(d["enabled"])
                d["android_background_notice"] = ANDROID_BG_NOTICE
                out.append(d)
            return out

    @staticmethod
    def create_automation(
        name: str,
        prompt: str,
        description: str = "",
        schedule_cron: str = "0 * * * *",
        interval_seconds: int = 3600,
        runtime: str = "local",
        max_retries: int = 3,
    ) -> Dict[str, Any]:
        aid = f"auto-{uuid.uuid4().hex[:8]}"
        now = _utc_now()
        next_ts = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(time.time() + max(60, interval_seconds)))
        with db_session() as conn:
            conn.execute(
                """INSERT INTO automations
                (id, name, description, prompt, schedule_cron, interval_seconds, runtime, enabled, next_run_at, last_status, retry_count, max_retries, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, 'scheduled', 0, ?, ?, ?)""",
                (aid, name, description, prompt, schedule_cron, interval_seconds, runtime, next_ts, max_retries, now, now),
            )
            row = conn.execute("SELECT * FROM automations WHERE id = ?", (aid,)).fetchone()
        log_activity("INFO", "automation", f"Created automation '{name}' on {runtime} runtime", {"automation_id": aid})
        d = dict(row)
        d["enabled"] = bool(d["enabled"])
        d["android_background_notice"] = ANDROID_BG_NOTICE
        return d

    @staticmethod
    def toggle_automation(automation_id: str, enabled: bool) -> Optional[Dict[str, Any]]:
        now = _utc_now()
        with db_session() as conn:
            row = conn.execute("SELECT * FROM automations WHERE id = ?", (automation_id,)).fetchone()
            if not row:
                return None
            conn.execute(
                "UPDATE automations SET enabled = ?, last_status = ?, updated_at = ? WHERE id = ?",
                (int(enabled), "scheduled" if enabled else "stopped", now, automation_id),
            )
            updated = conn.execute("SELECT * FROM automations WHERE id = ?", (automation_id,)).fetchone()
            return dict(updated) if updated else None

    @staticmethod
    async def run_automation_now(automation_id: str) -> Optional[Dict[str, Any]]:
        now = _utc_now()
        with db_session() as conn:
            row = conn.execute("SELECT * FROM automations WHERE id = ?", (automation_id,)).fetchone()
            if not row:
                return None
            auto = dict(row)

        try:
            task = await AgentEngine.run_workflow(
                prompt=auto["prompt"],
                requested_runtime=auto["runtime"],
            )
            status_val = task["status"] if task else "completed"
            retry_cnt = 0
        except Exception as exc:
            status_val = f"failed: {exc}"
            retry_cnt = min(auto["retry_count"] + 1, auto["max_retries"])
            task = None

        next_ts = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(time.time() + max(60, auto["interval_seconds"])))
        with db_session() as conn:
            conn.execute(
                "UPDATE automations SET last_run_at = ?, next_run_at = ?, last_status = ?, retry_count = ?, updated_at = ? WHERE id = ?",
                (now, next_ts, status_val, retry_cnt, now, automation_id),
            )
            updated = conn.execute("SELECT * FROM automations WHERE id = ?", (automation_id,)).fetchone()
        return {"automation": dict(updated), "triggered_task": task}
