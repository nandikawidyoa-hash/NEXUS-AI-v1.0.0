import re
import uuid
from typing import Any, Dict, List, Optional
from nexus.database.db import _utc_now, db_session, get_setting, log_activity
from nexus.security.crypto import contains_sensitive_secret, redact_secrets

VALID_CATEGORIES = ("short_term", "long_term", "project", "conversation", "task")

class MemoryManager:
    @staticmethod
    def store_memory(
        category: str,
        key: str,
        content: str,
        project_id: Optional[str] = None,
        importance: int = 3,
    ) -> Dict[str, Any]:
        if category not in VALID_CATEGORIES:
            category = "short_term"
        if contains_sensitive_secret(content) or contains_sensitive_secret(key):
            content = redact_secrets(content)
            key = redact_secrets(key)
            log_activity("WARNING", "memory", "Secret pattern detected and redacted before saving to memory", {"key": key})

        mem_id = f"mem-{uuid.uuid4().hex[:10]}"
        now = _utc_now()
        with db_session() as conn:
            conn.execute(
                """INSERT INTO memories
                (id, category, project_id, key, content, importance, synced_to_cloud, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)""",
                (mem_id, category, project_id, key, content, max(1, min(5, importance)), now, now),
            )
            conn.execute(
                "INSERT INTO memories_fts (memory_id, category, key, content) VALUES (?, ?, ?, ?)",
                (mem_id, category, key, content),
            )
        return {
            "id": mem_id,
            "category": category,
            "project_id": project_id,
            "key": key,
            "content": content,
            "importance": importance,
            "synced_to_cloud": False,
            "created_at": now,
            "updated_at": now,
        }

    @staticmethod
    def list_memories(category: Optional[str] = None, project_id: Optional[str] = None, limit: int = 100) -> List[Dict[str, Any]]:
        query = "SELECT * FROM memories WHERE 1=1"
        params: List[Any] = []
        if category:
            query += " AND category = ?"
            params.append(category)
        if project_id:
            query += " AND project_id = ?"
            params.append(project_id)
        query += " ORDER BY importance DESC, updated_at DESC LIMIT ?"
        params.append(limit)
        with db_session() as conn:
            rows = conn.execute(query, tuple(params)).fetchall()
            return [dict(r) for r in rows]

    @staticmethod
    def search_memories(query_text: str, category: Optional[str] = None, limit: int = 20) -> List[Dict[str, Any]]:
        tokens = re.findall(r"[A-Za-z0-9_]+", query_text or "")
        if not tokens:
            return MemoryManager.list_memories(category=category, limit=limit)
        fts_query = " OR ".join(f'"{t}"*' for t in tokens[:8])
        sql = """
            SELECT m.* FROM memories_fts f
            JOIN memories m ON m.id = f.memory_id
            WHERE memories_fts MATCH ?
        """
        params: List[Any] = [fts_query]
        if category:
            sql += " AND m.category = ?"
            params.append(category)
        sql += " ORDER BY rank LIMIT ?"
        params.append(limit)
        with db_session() as conn:
            rows = conn.execute(sql, tuple(params)).fetchall()
            return [dict(r) for r in rows]

    @staticmethod
    def update_memory(memory_id: str, key: Optional[str] = None, content: Optional[str] = None, importance: Optional[int] = None) -> Optional[Dict[str, Any]]:
        now = _utc_now()
        with db_session() as conn:
            existing = conn.execute("SELECT * FROM memories WHERE id = ?", (memory_id,)).fetchone()
            if not existing:
                return None
            new_key = redact_secrets(key) if key is not None else existing["key"]
            new_content = redact_secrets(content) if content is not None else existing["content"]
            new_imp = importance if importance is not None else existing["importance"]
            conn.execute(
                "UPDATE memories SET key = ?, content = ?, importance = ?, synced_to_cloud = 0, updated_at = ? WHERE id = ?",
                (new_key, new_content, new_imp, now, memory_id),
            )
            conn.execute("DELETE FROM memories_fts WHERE memory_id = ?", (memory_id,))
            conn.execute(
                "INSERT INTO memories_fts (memory_id, category, key, content) VALUES (?, ?, ?, ?)",
                (memory_id, existing["category"], new_key, new_content),
            )
            updated = conn.execute("SELECT * FROM memories WHERE id = ?", (memory_id,)).fetchone()
            return dict(updated) if updated else None

    @staticmethod
    def delete_memory(memory_id: str) -> bool:
        with db_session() as conn:
            cur = conn.execute("DELETE FROM memories WHERE id = ?", (memory_id,))
            conn.execute("DELETE FROM memories_fts WHERE memory_id = ?", (memory_id,))
            return cur.rowcount > 0

    @staticmethod
    def sync_unsynced_memories() -> Dict[str, Any]:
        with db_session() as conn:
            rows = conn.execute("SELECT id FROM memories WHERE synced_to_cloud = 0").fetchall()
            ids = [r["id"] for r in rows]
            if ids:
                conn.executemany("UPDATE memories SET synced_to_cloud = 1 WHERE id = ?", [(i,) for i in ids])
        log_activity("INFO", "memory", "Memory synchronization completed", {"synced_count": len(ids)})
        return {"synced_count": len(ids), "synced_ids": ids, "timestamp": _utc_now()}
