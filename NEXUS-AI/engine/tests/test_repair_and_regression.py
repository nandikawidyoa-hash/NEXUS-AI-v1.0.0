import os
import re
import shutil
import tempfile
import unittest
from pathlib import Path
from starlette.testclient import TestClient

from nexus.config import SKILLS_DIR
from nexus.main import create_app
from nexus.skills.manager import SkillManager


def kotlin_parse_message_segments_mirror(raw: str):
    """Exact mirror of CommonComponents.kt::parseMessageSegments for automated verification."""
    if not raw:
        return [{"isCode": False, "language": "", "content": ""}]
    segments = []
    fence_regex = re.compile(r"```([a-zA-Z0-9_+.-]*)?[ \t]*\r?\n?([\s\S]*?)(?:```|$)")
    last_index = 0
    for match in fence_regex.finditer(raw):
        start = match.start()
        if start > last_index:
            text_part = raw[last_index:start].strip("\n\r")
            if text_part:
                segments.append({"isCode": False, "language": "", "content": text_part})
        lang = (match.group(1) or "").strip() or "code"
        code_content = match.group(2) or ""
        if code_content.endswith("\r\n"):
            code_content = code_content[:-2]
        elif code_content.endswith("\n"):
            code_content = code_content[:-1]
        segments.append({"isCode": True, "language": lang, "content": code_content})
        last_index = match.end()
    if last_index < len(raw):
        tail = raw[last_index:].strip("\n\r")
        if tail:
            segments.append({"isCode": False, "language": "", "content": tail})
    if not segments:
        segments.append({"isCode": False, "language": "", "content": raw})
    return segments


def test_copy_code_short_long_multiblock_and_special_chars():
    short_code = "def greet(name: str) -> str:\n    if not name:\n        return 'Hello, World!'\n    return f'Hello, {name}!'"
    msg1 = f"Berikut contoh fungsi Python:\n```python\n{short_code}\n```\nSemoga membantu!"
    segs1 = kotlin_parse_message_segments_mirror(msg1)
    assert len(segs1) == 3
    assert segs1[1]["isCode"] is True
    assert segs1[1]["language"] == "python"
    assert segs1[1]["content"] == short_code

    long_lines = ["class DataProcessor:"]
    for i in range(1, 200):
        long_lines.append(f"    def step_{i}(self, val):\n        return val + {i}")
    long_code = "\n".join(long_lines)
    msg2 = f"Kode panjang:\n```python\n{long_code}\n```"
    segs2 = kotlin_parse_message_segments_mirror(msg2)
    assert segs2[1]["isCode"] is True
    assert segs2[1]["content"] == long_code
    assert len(segs2[1]["content"].splitlines()) == len(long_code.splitlines())

    kt_code = "fun main() {\n    val map = mapOf(\"key\" to \"<val>&'$100\")\n    println(map[\"key\"])\n}"
    sh_code = "#!/usr/bin/env bash\nexport PATH=\"$HOME/bin:$PATH\"\necho \"Done & Verified!\""
    json_code = "{\n  \"status\": \"ok\",\n  \"items\": [1, 2, 3]\n}"
    msg3 = (
        f"Penjelasan awal.\n```kotlin\n{kt_code}\n```\n"
        f"Lalu jalankan script:\n```bash\n{sh_code}\n```\n"
        f"Output JSON:\n```json\n{json_code}\n```\nPenutup."
    )
    segs3 = kotlin_parse_message_segments_mirror(msg3)
    code_segs = [s for s in segs3 if s["isCode"]]
    assert len(code_segs) == 3
    assert code_segs[0]["language"] == "kotlin" and code_segs[0]["content"] == kt_code
    assert code_segs[1]["language"] == "bash" and code_segs[1]["content"] == sh_code
    assert code_segs[2]["language"] == "json" and code_segs[2]["content"] == json_code


def test_skill_manager_full_lifecycle_and_error_handling():
    tmp_root = Path(tempfile.mkdtemp(prefix="nexus_skill_repair_"))
    tmp_skills = tmp_root / "skills"
    shutil.copytree(SKILLS_DIR, tmp_skills)

    prev_skills_env = os.environ.get("NEXUS_SKILLS_DIR")
    prev_db_env = os.environ.get("NEXUS_DB_PATH")
    os.environ["NEXUS_SKILLS_DIR"] = str(tmp_skills)
    os.environ["NEXUS_DB_PATH"] = str(tmp_root / "repair_test.db")
    from nexus.config import DEFAULT_LOCAL_TOKEN
    token = os.environ.get("NEXUS_API_TOKEN", DEFAULT_LOCAL_TOKEN)
    headers = {"Authorization": f"Bearer {token}"}

    try:
        new_skill_dir = tmp_skills / "test-git-auditor"
        new_skill_dir.mkdir(parents=True, exist_ok=True)
        valid_md = (
            "---\n"
            "name: test-git-auditor\n"
            "description: Skill penguji untuk audit repositori Git dan kualitas kode.\n"
            "version: 1.2.0\n"
            "required_tools:\n"
            "  - file_read\n"
            "  - file_search\n"
            "required_permissions:\n"
            "  - storage_workspace\n"
            "---\n"
            "# Test Git Auditor\n"
            "Selalu periksa struktur file dan laporkan ringkasan audit dengan rapi."
        )
        (new_skill_dir / "SKILL.md").write_text(valid_md, encoding="utf-8")

        broken_skill_dir = tmp_skills / "broken-yaml-skill"
        broken_skill_dir.mkdir(parents=True, exist_ok=True)
        (broken_skill_dir / "SKILL.md").write_text("---\nname: [unclosed yaml\n---\n", encoding="utf-8")

        empty_skill_dir = tmp_skills / "empty-folder-no-md"
        empty_skill_dir.mkdir(parents=True, exist_ok=True)

        missing_tool_dir = tmp_skills / "missing-tool-skill"
        missing_tool_dir.mkdir(parents=True, exist_ok=True)
        (missing_tool_dir / "SKILL.md").write_text(
            "---\nname: missing-tool-skill\ndescription: Butuh tool yang tidak ada\nrequired_tools:\n  - quantum_teleporter_tool\n---\n# Instruksi\nTes tool.",
            encoding="utf-8",
        )

        app = create_app()
        client = TestClient(app)

        scan_res = client.post("/skills/scan", headers=headers)
        assert scan_res.status_code == 200
        scan_data = scan_res.json()
        skills_by_name = {s["name"]: s for s in scan_data["skills"]}

        assert "test-git-auditor" in skills_by_name
        auditor = skills_by_name["test-git-auditor"]
        assert auditor["status"] == "discovered"
        assert auditor["enabled"] is False
        assert auditor["version"] == "1.2.0"
        assert auditor["required_tools"] == ["file_read", "file_search"]
        assert auditor["required_permissions"] == ["storage_workspace"]
        assert auditor["validation_errors"] == []

        raw_res = client.get(f"/skills/{auditor['id']}/raw", headers=headers)
        assert raw_res.status_code == 200
        assert "Test Git Auditor" in raw_res.json()["raw_content"]

        assert skills_by_name["broken-yaml-skill"]["status"] == "error"
        assert len(skills_by_name["broken-yaml-skill"]["validation_errors"]) > 0

        assert skills_by_name["empty-folder-no-md"]["status"] == "error"
        assert any("SKILL.md" in e for e in skills_by_name["empty-folder-no-md"]["validation_errors"])

        assert skills_by_name["missing-tool-skill"]["status"] == "error"
        assert "quantum_teleporter_tool" in skills_by_name["missing-tool-skill"]["missing_tools"]

        bad_install = client.post(f"/skills/{skills_by_name['broken-yaml-skill']['id']}/install", headers=headers)
        assert bad_install.status_code == 400

        inst_res = client.post(f"/skills/{auditor['id']}/install?enable=false", headers=headers)
        assert inst_res.status_code == 200
        assert inst_res.json()["skill"]["status"] == "installed"
        assert inst_res.json()["skill"]["enabled"] is False

        en_res = client.post(f"/skills/{auditor['id']}/enable", headers=headers)
        assert en_res.status_code == 200
        assert en_res.json()["skill"]["status"] == "enabled"
        assert en_res.json()["skill"]["enabled"] is True

        chat_res = client.post(
            "/chat",
            headers=headers,
            json={"message": "Tolong jalankan test-git-auditor untuk memeriksa project ini."},
        )
        assert chat_res.status_code == 200
        task_obj = chat_res.json()["task"]
        plan_step = [s for s in task_obj["steps"] if s["phase"] == "plan"][0]
        assert "test-git-auditor" in plan_step["output"]

        dis_res = client.post(f"/skills/{auditor['id']}/disable", headers=headers)
        assert dis_res.status_code == 200
        assert dis_res.json()["skill"]["status"] == "disabled"
        assert dis_res.json()["skill"]["enabled"] is False
        ctx_after_disable = SkillManager.get_active_skills_context("test-git-auditor")
        assert "test-git-auditor" not in [s["name"] for s in ctx_after_disable["active_skills"]]

        uninst_res = client.post(
            f"/skills/{auditor['id']}/uninstall?delete_files=true",
            headers=headers,
        )
        assert uninst_res.status_code == 200
        assert uninst_res.json()["uninstalled"] is True

    finally:
        if prev_skills_env is None:
            os.environ.pop("NEXUS_SKILLS_DIR", None)
        else:
            os.environ["NEXUS_SKILLS_DIR"] = prev_skills_env
        if prev_db_env is None:
            os.environ.pop("NEXUS_DB_PATH", None)
        else:
            os.environ["NEXUS_DB_PATH"] = prev_db_env
        shutil.rmtree(tmp_root, ignore_errors=True)


class TestRepairAndRegression(unittest.TestCase):
    def test_01_copy_code_segments(self):
        test_copy_code_short_long_multiblock_and_special_chars()

    def test_02_skill_manager_full_lifecycle(self):
        test_skill_manager_full_lifecycle_and_error_handling()


if __name__ == "__main__":
    unittest.main(verbosity=2)
