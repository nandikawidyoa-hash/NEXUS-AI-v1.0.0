import time
from typing import Any, Dict, List
from nexus.database.db import _utc_now, db_session, get_setting, log_activity, set_setting
from nexus.memory.manager import MemoryManager

class HybridRuntimeManager:
    @staticmethod
    def get_status() -> Dict[str, Any]:
        active_rt = get_setting("active_runtime", "local")
        cloud_url = get_setting("cloud_runtime_url", "")
        cloud_enabled = bool(get_setting("cloud_runtime_enabled", False))
        with db_session() as conn:
            unsynced_mem = conn.execute("SELECT COUNT(*) as c FROM memories WHERE synced_to_cloud = 0").fetchone()["c"]
            unsynced_tasks = conn.execute("SELECT COUNT(*) as c FROM tasks WHERE synced_to_cloud = 0").fetchone()["c"]
            cloud_queued = conn.execute("SELECT COUNT(*) as c FROM tasks WHERE runtime = 'cloud' AND status IN ('pending','running')").fetchone()["c"]

        return {
            "active_runtime": active_rt,
            "local_runtime_online": True,
            "cloud_runtime_enabled": cloud_enabled,
            "cloud_runtime_url": cloud_url or "Not configured (Local-First Mode)",
            "cloud_queued_tasks": cloud_queued,
            "unsynced_memories": unsynced_mem,
            "unsynced_tasks": unsynced_tasks,
            "hardware_capability_notice": (
                "Cloud Runtime hanya dapat menjalankan riset web, pemrosesan teks/API, dan antrean cloud. "
                "Cloud TIDAK DAPAT mengakses file lokal HP, kamera, baterai, atau Android Intents ketika HP/Termux sedang offline."
            ),
            "timestamp": _utc_now(),
        }

    @staticmethod
    def configure_cloud(cloud_url: str, enabled: bool, active_runtime: str = "local") -> Dict[str, Any]:
        if active_runtime not in ("local", "cloud", "hybrid"):
            active_runtime = "local"
        set_setting("cloud_runtime_url", cloud_url.strip())
        set_setting("cloud_runtime_enabled", bool(enabled))
        set_setting("active_runtime", active_runtime)
        log_activity("INFO", "hybrid_runtime", f"Updated hybrid runtime config (active={active_runtime}, cloud_enabled={enabled})")
        return HybridRuntimeManager.get_status()

    @staticmethod
    def synchronize_state() -> Dict[str, Any]:
        mem_sync = MemoryManager.sync_unsynced_memories()
        with db_session() as conn:
            rows = conn.execute("SELECT id FROM tasks WHERE synced_to_cloud = 0").fetchall()
            t_ids = [r["id"] for r in rows]
            if t_ids:
                conn.executemany("UPDATE tasks SET synced_to_cloud = 1 WHERE id = ?", [(tid,) for tid in t_ids])
        log_activity("INFO", "hybrid_runtime", "Synchronized tasks and memories between Local and Cloud queues", {
            "tasks_synced": len(t_ids),
            "memories_synced": mem_sync["synced_count"],
        })
        return {
            "status": "synchronized",
            "tasks_synced": len(t_ids),
            "memories_synced": mem_sync["synced_count"],
            "timestamp": _utc_now(),
        }
