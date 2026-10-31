import json
import re
import shutil
import uuid
import yaml
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
from nexus.config import SKILLS_DIR
from nexus.database.db import _utc_now, db_session, log_activity

DANGEROUS_SKILL_PATTERNS = [
    re.compile(r"rm\s+-rf\s+/"),
    re.compile(r"curl\s+.*\|\s*(sh|bash)"),
    re.compile(r"wget\s+.*\|\s*(sh|bash)"),
    re.compile(r"(?i)exfiltrate|steal\s+api\s+key|send\s+env"),
]

class SkillManager:
    @staticmethod
    def parse_skill_markdown(raw_text: str) -> Tuple[Dict[str, Any], str]:
        meta: Dict[str, Any] = {}
        body = raw_text.strip()
        if body.startswith("---"):
            parts = body.split("---", 2)
            if len(parts) >= 3:
                try:
                    loaded = yaml.safe_load(parts[1])
                    if isinstance(loaded, dict):
                        meta = loaded
                except Exception:
                    pass
                body = parts[2].strip()
        return meta, body

    @staticmethod
    def inspect_skill_security(raw_text: str) -> Tuple[str, str]:
        findings = []
        for pat in DANGEROUS_SKILL_PATTERNS:
            if pat.search(raw_text):
                findings.append(f"Pola berbahaya terdeteksi: {pat.pattern}")
        if findings:
            return ("blocked", "; ".join(findings))
        return ("verified", "Tidak ditemukan instruksi berbahaya atau eksfiltrasi data.")

    @staticmethod
    def sync_filesystem_skills():
        now = _utc_now()
        if not SKILLS_DIR.exists():
            return
        for skill_md in sorted(SKILLS_DIR.glob("*/SKILL.md")):
            raw = skill_md.read_text(encoding="utf-8", errors="replace")
            meta, body = SkillManager.parse_skill_markdown(raw)
            sec_status, sec_notes = SkillManager.inspect_skill_security(raw)
            name = str(meta.get("name") or skill_md.parent.name).strip()
            desc = str(meta.get("description") or "Modular NEXUS AI Skill").strip()
            version = str(meta.get("version") or "1.0.0").strip()
            req_tools = meta.get("required_tools") or []
            req_perms = meta.get("required_permissions") or []
            enabled = 1 if meta.get("enabled", True) and sec_status == "verified" else 0
            is_builtin = 1 if skill_md.parent.name in ("coding", "research", "file-management", "android") else 0

            with db_session() as conn:
                existing = conn.execute("SELECT id, enabled FROM skills WHERE name = ?", (name,)).fetchone()
                if existing:
                    conn.execute(
                        """UPDATE skills
                        SET description = ?, version = ?, folder_path = ?, instructions = ?,
                            required_tools_json = ?, required_permissions_json = ?,
                            security_status = ?, security_notes = ?, updated_at = ?
                        WHERE name = ?""",
                        (
                            desc,
                            version,
                            str(skill_md.parent),
                            body,
                            json.dumps(req_tools),
                            json.dumps(req_perms),
                            sec_status,
                            sec_notes,
                            now,
                            name,
                        ),
                    )
                else:
                    conn.execute(
                        """INSERT INTO skills
                        (id, name, description, version, folder_path, instructions, required_tools_json,
                         required_permissions_json, enabled, is_builtin, security_status, security_notes, installed_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                        (
                            f"skill-{uuid.uuid4().hex[:8]}",
                            name,
                            desc,
                            version,
                            str(skill_md.parent),
                            body,
                            json.dumps(req_tools),
                            json.dumps(req_perms),
                            enabled,
                            is_builtin,
                            sec_status,
                            sec_notes,
                            now,
                            now,
                        ),
                    )

    @staticmethod
    def list_skills() -> List[Dict[str, Any]]:
        SkillManager.sync_filesystem_skills()
        with db_session() as conn:
            rows = conn.execute("SELECT * FROM skills ORDER BY is_builtin DESC, name ASC").fetchall()
            out = []
            for r in rows:
                d = dict(r)
                d["required_tools"] = json.loads(d.pop("required_tools_json", "[]"))
                d["required_permissions"] = json.loads(d.pop("required_permissions_json", "[]"))
                d["enabled"] = bool(d["enabled"])
                d["is_builtin"] = bool(d["is_builtin"])
                out.append(d)
            return out

    @staticmethod
    def install_skill(folder_name: str, skill_md_content: str) -> Dict[str, Any]:
        clean_folder = re.sub(r"[^a-zA-Z0-9_\-]", "-", folder_name.strip().lower()).strip("-")
        if not clean_folder:
            raise ValueError("Nama folder skill tidak valid.")
        sec_status, sec_notes = SkillManager.inspect_skill_security(skill_md_content)
        if sec_status == "blocked":
            log_activity("ERROR", "skills", f"Blocked unsafe third-party skill: {clean_folder}", {"notes": sec_notes})
            raise PermissionError(f"Skill ditolak oleh pemeriksa keamanan: {sec_notes}")

        target_dir = SKILLS_DIR / clean_folder
        target_dir.mkdir(parents=True, exist_ok=True)
        (target_dir / "SKILL.md").write_text(skill_md_content.strip() + "\n", encoding="utf-8")
        SkillManager.sync_filesystem_skills()
        meta, _ = SkillManager.parse_skill_markdown(skill_md_content)
        skill_name = str(meta.get("name") or clean_folder)
        with db_session() as conn:
            row = conn.execute("SELECT * FROM skills WHERE name = ?", (skill_name,)).fetchone()
            return dict(row) if row else {"name": skill_name, "status": "installed"}

    @staticmethod
    def toggle_skill(skill_id: str, enabled: bool) -> Optional[Dict[str, Any]]:
        now = _utc_now()
        with db_session() as conn:
            row = conn.execute("SELECT * FROM skills WHERE id = ?", (skill_id,)).fetchone()
            if not row:
                return None
            if row["security_status"] == "blocked" and enabled:
                raise PermissionError("Skill yang diblokir karena alasan keamanan tidak dapat diaktifkan.")
            conn.execute("UPDATE skills SET enabled = ?, updated_at = ? WHERE id = ?", (int(enabled), now, skill_id))
            updated = conn.execute("SELECT * FROM skills WHERE id = ?", (skill_id,)).fetchone()
            return dict(updated) if updated else None

    @staticmethod
    def delete_skill(skill_id: str) -> bool:
        with db_session() as conn:
            row = conn.execute("SELECT * FROM skills WHERE id = ?", (skill_id,)).fetchone()
            if not row:
                return False
            if row["is_builtin"]:
                raise PermissionError("Skill bawaan sistem tidak boleh dihapus; gunakan fitur nonaktifkan.")
            folder = Path(row["folder_path"])
            if folder.exists() and str(folder.resolve()).startswith(str(SKILLS_DIR.resolve())):
                shutil.rmtree(folder, ignore_errors=True)
            conn.execute("DELETE FROM skills WHERE id = ?", (skill_id,))
            return True
