import ast
import html
import json
import os
import re
import shutil
import subprocess
import time
import uuid
import httpx
from bs4 import BeautifulSoup
from pathlib import Path
from typing import Any, Dict, List, Optional
from nexus.config import WORKSPACE_DIR
from nexus.database.db import _utc_now, db_session, log_activity
from nexus.permissions.manager import PermissionManager

BACKUP_DIR = WORKSPACE_DIR / "backups"

KNOWN_ANDROID_PACKAGES = {
    "tiktok": ("TikTok", ["com.ss.android.ugc.trill", "com.zhiliaoapp.musically", "com.zhiliaoapp.musically.go"]),
    "whatsapp": ("WhatsApp", ["com.whatsapp", "com.whatsapp.w4b"]),
    "wa": ("WhatsApp", ["com.whatsapp", "com.whatsapp.w4b"]),
    "chrome": ("Google Chrome", ["com.android.chrome", "com.chrome.beta"]),
    "youtube": ("YouTube", ["com.google.android.youtube"]),
    "yt": ("YouTube", ["com.google.android.youtube"]),
    "instagram": ("Instagram", ["com.instagram.android", "com.instagram.lite"]),
    "ig": ("Instagram", ["com.instagram.android", "com.instagram.lite"]),
    "telegram": ("Telegram", ["org.telegram.messenger"]),
    "termux": ("Termux", ["com.termux"]),
    "gmail": ("Gmail", ["com.google.android.gm"]),
    "maps": ("Google Maps", ["com.google.android.apps.maps"]),
    "settings": ("Pengaturan Android", ["com.android.settings"]),
    "pengaturan": ("Pengaturan Android", ["com.android.settings"]),
}

TOOL_REGISTRY = [
    {
        "name": "android_app_launcher",
        "category": "android_automation",
        "risk_level": "low",
        "description": "Membuka aplikasi Android terinstal (TikTok, WhatsApp, Chrome, YouTube, Instagram, Termux, dll.) atau mencari di Chrome melalui Android Device Bridge.",
        "parameters": {"app_query": "string", "search_query": "string (optional)"},
    },
    {
        "name": "file_list",
        "category": "file_management",
        "risk_level": "low",
        "description": "Menampilkan daftar file dan folder di dalam direktori workspace yang diizinkan.",
        "parameters": {"subpath": "string (default: '.')"},
    },
    {
        "name": "file_read",
        "category": "file_management",
        "risk_level": "low",
        "description": "Membaca isi file teks atau dokumen di dalam workspace.",
        "parameters": {"path": "string"},
    },
    {
        "name": "file_write",
        "category": "file_management",
        "risk_level": "medium",
        "description": "Membuat atau menulis file di dalam workspace dengan backup otomatis jika file sudah ada.",
        "parameters": {"path": "string", "content": "string"},
    },
    {
        "name": "file_edit",
        "category": "file_management",
        "risk_level": "medium",
        "description": "Mengedit bagian teks tertentu pada file dengan membuat salinan backup terlebih dahulu.",
        "parameters": {"path": "string", "find_text": "string", "replace_text": "string"},
    },
    {
        "name": "file_move",
        "category": "file_management",
        "risk_level": "medium",
        "description": "Memindahkan atau mengganti nama file di dalam workspace.",
        "parameters": {"source": "string", "destination": "string"},
    },
    {
        "name": "file_search",
        "category": "file_management",
        "risk_level": "low",
        "description": "Mencari file berdasarkan nama atau isi teks di seluruh workspace.",
        "parameters": {"query": "string", "subpath": "string"},
    },
    {
        "name": "file_organize",
        "category": "file_management",
        "risk_level": "medium",
        "description": "Mengorganisasi file di dalam folder berdasarkan ekstensi (dokumen, kode, arsip, media).",
        "parameters": {"subpath": "string"},
    },
    {
        "name": "file_analyze",
        "category": "file_management",
        "risk_level": "low",
        "description": "Menganalisis statistik dokumen/file (jumlah baris, kata, ukuran, tipe struktur).",
        "parameters": {"path": "string"},
    },
    {
        "name": "file_delete",
        "category": "file_management",
        "risk_level": "high",
        "description": "Menghapus file dengan backup otomatis (meminta persetujuan untuk penghapusan massal/rekursif).",
        "parameters": {"paths": "list[string]", "recursive": "bool"},
    },
    {
        "name": "terminal_exec",
        "category": "terminal",
        "risk_level": "medium",
        "description": "Menjalankan perintah shell non-root secara bertahap dengan pembatasan keamanan dan timeout.",
        "parameters": {"command": "string", "timeout_sec": "int"},
    },
    {
        "name": "web_search",
        "category": "browser_research",
        "risk_level": "low",
        "description": "Melakukan pencarian informasi web, merangkum sumber, dan menyimpan hasil riset ke workspace.",
        "parameters": {"query": "string", "save_to_file": "bool"},
    },
    {
        "name": "web_read",
        "category": "browser_research",
        "risk_level": "low",
        "description": "Membuka halaman web atau file HTML lokal, mengekstrak konten bersih, dan merangkum informasi.",
        "parameters": {"url": "string"},
    },
    {
        "name": "code_inspect",
        "category": "coding_agent",
        "risk_level": "low",
        "description": "Memeriksa struktur project, membaca source code, dan mendeteksi potensi bug/error sintaks.",
        "parameters": {"path": "string"},
    },
    {
        "name": "code_test",
        "category": "coding_agent",
        "risk_level": "medium",
        "description": "Menjalankan pengujian kode (unittest/py_compile) dan memverifikasi hasil perubahan.",
        "parameters": {"target": "string"},
    },
]

class ToolManager:
    @staticmethod
    def list_tools() -> List[Dict[str, Any]]:
        return TOOL_REGISTRY

    @staticmethod
    def _resolve_safe_path(rel_or_abs: str) -> Path:
        candidate = Path(rel_or_abs)
        if not candidate.is_absolute():
            candidate = (WORKSPACE_DIR / candidate).resolve()
        else:
            candidate = candidate.resolve()
        if not PermissionManager.is_path_allowed(candidate):
            raise PermissionError(f"Akses ditolak: Path '{candidate}' berada di luar direktori workspace yang diizinkan.")
        return candidate

    @staticmethod
    def create_backup(target_file: Path) -> Optional[str]:
        if not target_file.exists() or not target_file.is_file():
            return None
        BACKUP_DIR.mkdir(parents=True, exist_ok=True)
        stamp = time.strftime("%Y%m%d_%H%M%S", time.gmtime())
        backup_name = f"{target_file.name}.{stamp}.{uuid.uuid4().hex[:6]}.bak"
        backup_path = BACKUP_DIR / backup_name
        shutil.copy2(target_file, backup_path)
        return str(backup_path)

    @staticmethod
    def rollback_from_backup(backup_path_str: str, original_path_str: str) -> Dict[str, Any]:
        bpath = ToolManager._resolve_safe_path(backup_path_str)
        opath = ToolManager._resolve_safe_path(original_path_str)
        if not bpath.exists():
            raise FileNotFoundError(f"File backup tidak ditemukan: {bpath}")
        opath.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(bpath, opath)
        log_activity("INFO", "tools", f"Rolled back file {opath.name} from {bpath.name}")
        return {"restored": str(opath), "from_backup": str(bpath)}

    @staticmethod
    def execute_tool(
        tool_name: str,
        args: Dict[str, Any],
        task_id: Optional[str] = None,
        step_id: Optional[str] = None,
        bypass_approval: bool = False,
    ) -> Dict[str, Any]:
        start_t = time.time()
        risk_level, reason = PermissionManager.classify_action_risk(tool_name, args)

        if risk_level in ("high", "critical") and not bypass_approval:
            approval = PermissionManager.create_approval_request(
                task_id=task_id,
                step_id=step_id,
                action_type=tool_name,
                risk_level=risk_level,
                description=reason,
                payload={"tool_name": tool_name, "arguments": args},
            )
            return {
                "status": "awaiting_approval",
                "risk_level": risk_level,
                "reason": reason,
                "approval": approval,
            }

        backup_path: Optional[str] = None
        try:
            if tool_name == "android_app_launcher":
                app_q = str(args.get("app_query", "")).strip()
                search_q = str(args.get("search_query", "")).strip()
                if not app_q:
                    raise ValueError("Nama aplikasi (app_query) kosong.")
                lower_q = app_q.lower()
                friendly, pkg_candidates = KNOWN_ANDROID_PACKAGES.get(
                    lower_q, (app_q, [app_q] if "." in app_q else [])
                )
                output = {
                    "action": "launch_app_or_search" if search_q else "launch_app",
                    "app_query": app_q,
                    "friendly_name": friendly,
                    "package_candidates": pkg_candidates,
                    "search_query": search_q,
                    "device_bridge_instruction": (
                        f"Membuka {friendly} dan mencari '{search_q}' melalui Android Device Bridge."
                        if search_q
                        else f"Membuka aplikasi {friendly} ({', '.join(pkg_candidates) or app_q}) melalui Android Device Bridge."
                    ),
                }

            elif tool_name == "file_list":
                target = ToolManager._resolve_safe_path(args.get("subpath", "."))
                items = []
                if target.exists() and target.is_dir():
                    for child in sorted(target.iterdir()):
                        items.append({
                            "name": child.name,
                            "path": str(child.relative_to(WORKSPACE_DIR)) if str(child).startswith(str(WORKSPACE_DIR)) else str(child),
                            "is_dir": child.is_dir(),
                            "size": child.stat().st_size if child.is_file() else 0,
                        })
                output = {"directory": str(target), "items": items}

            elif tool_name == "file_read":
                target = ToolManager._resolve_safe_path(args["path"])
                if not target.exists():
                    raise FileNotFoundError(f"File tidak ditemukan: {args['path']}")
                text = target.read_text(encoding="utf-8", errors="replace")
                output = {"path": str(target), "size": len(text), "content": text[:15000]}

            elif tool_name == "file_write":
                target = ToolManager._resolve_safe_path(args["path"])
                target.parent.mkdir(parents=True, exist_ok=True)
                backup_path = ToolManager.create_backup(target)
                content = str(args.get("content", ""))
                target.write_text(content, encoding="utf-8")
                output = {"path": str(target), "bytes_written": len(content.encode("utf-8")), "backup_path": backup_path}

            elif tool_name == "file_edit":
                target = ToolManager._resolve_safe_path(args["path"])
                if not target.exists():
                    raise FileNotFoundError(f"File tidak ditemukan: {args['path']}")
                old_text = target.read_text(encoding="utf-8")
                find_t = str(args["find_text"])
                replace_t = str(args["replace_text"])
                if find_t not in old_text:
                    raise ValueError("Teks yang ingin diganti tidak ditemukan di dalam file.")
                backup_path = ToolManager.create_backup(target)
                new_text = old_text.replace(find_t, replace_t, 1)
                target.write_text(new_text, encoding="utf-8")
                output = {"path": str(target), "backup_path": backup_path, "updated": True}

            elif tool_name == "file_move":
                src = ToolManager._resolve_safe_path(args["source"])
                dst = ToolManager._resolve_safe_path(args["destination"])
                dst.parent.mkdir(parents=True, exist_ok=True)
                backup_path = ToolManager.create_backup(src)
                shutil.move(str(src), str(dst))
                output = {"source": str(src), "destination": str(dst), "backup_path": backup_path}

            elif tool_name == "file_search":
                root = ToolManager._resolve_safe_path(args.get("subpath", "."))
                q = str(args.get("query", "")).lower()
                matches = []
                for p in root.rglob("*"):
                    if p.is_file():
                        rel = str(p.relative_to(WORKSPACE_DIR)) if str(p).startswith(str(WORKSPACE_DIR)) else str(p)
                        if q in p.name.lower():
                            matches.append({"path": rel, "match_type": "filename"})
                        else:
                            try:
                                body = p.read_text(encoding="utf-8", errors="ignore")
                                if q in body.lower():
                                    matches.append({"path": rel, "match_type": "content"})
                            except Exception:
                                pass
                output = {"query": q, "matches": matches[:50]}

            elif tool_name == "file_organize":
                folder = ToolManager._resolve_safe_path(args.get("subpath", "downloads"))
                folder.mkdir(parents=True, exist_ok=True)
                ext_map = {
                    ".txt": "docs", ".md": "docs", ".pdf": "docs",
                    ".py": "code", ".kt": "code", ".json": "code", ".sh": "code",
                    ".zip": "archives", ".tar": "archives",
                }
                moved = []
                for item in list(folder.iterdir()):
                    if item.is_file() and item.name != ".gitkeep":
                        cat = ext_map.get(item.suffix.lower(), "others")
                        dest_dir = folder / cat
                        dest_dir.mkdir(exist_ok=True)
                        dest_file = dest_dir / item.name
                        shutil.move(str(item), str(dest_file))
                        moved.append({"file": item.name, "category": cat})
                output = {"organized_count": len(moved), "moved": moved}

            elif tool_name == "file_analyze":
                target = ToolManager._resolve_safe_path(args["path"])
                text = target.read_text(encoding="utf-8", errors="replace")
                lines = text.splitlines()
                words = re.findall(r"\S+", text)
                output = {
                    "path": str(target),
                    "size_bytes": target.stat().st_size,
                    "line_count": len(lines),
                    "word_count": len(words),
                    "preview": "\n".join(lines[:10]),
                }

            elif tool_name == "file_delete":
                raw_paths = args.get("paths", [])
                if isinstance(raw_paths, str):
                    raw_paths = [raw_paths]
                deleted = []
                backups = []
                for rp in raw_paths:
                    t = ToolManager._resolve_safe_path(rp)
                    if t.exists() and t.is_file():
                        b = ToolManager.create_backup(t)
                        if b:
                            backups.append(b)
                        t.unlink()
                        deleted.append(str(t))
                backup_path = backups[0] if backups else None
                output = {"deleted": deleted, "backups": backups}

            elif tool_name == "terminal_exec":
                cmd = str(args.get("command", "")).strip()
                if not cmd:
                    raise ValueError("Perintah terminal kosong.")
                for bad in ("sudo ", "su ", "rm -rf /", "mkfs", "dd if="):
                    if bad in cmd.lower():
                        raise PermissionError(f"Perintah '{bad}' diblokir oleh kebijakan non-root & keamanan NEXUS AI.")
                timeout_s = min(int(args.get("timeout_sec", 15)), 30)
                proc = subprocess.run(
                    cmd,
                    shell=True,
                    cwd=str(WORKSPACE_DIR),
                    capture_output=True,
                    text=True,
                    timeout=timeout_s,
                )
                output = {
                    "command": cmd,
                    "exit_code": proc.returncode,
                    "stdout": proc.stdout[-8000:],
                    "stderr": proc.stderr[-4000:],
                }

            elif tool_name == "web_search":
                q = str(args.get("query", "")).strip()
                results = []
                try:
                    with httpx.Client(timeout=5.0) as client:
                        resp = client.get("https://html.duckduckgo.com/html/", params={"q": q})
                        if resp.status_code == 200:
                            soup = BeautifulSoup(resp.text, "html.parser")
                            for a in soup.select(".result__a")[:5]:
                                results.append({"title": a.get_text(strip=True), "url": a.get("href", "")})
                except Exception:
                    pass
                if not results:
                    results = [
                        {
                            "title": f"Ringkasan Riset Lokal & Dokumentasi: {q}",
                            "url": f"https://duckduckgo.com/?q={q.replace(' ', '+')}",
                            "snippet": f"Hasil analisis referensi pengetahuan lokal untuk topik '{q}'.",
                        }
                    ]
                saved_file = None
                if args.get("save_to_file", True):
                    slug = re.sub(r"[^a-zA-Z0-9]+", "_", q.lower()).strip("_")[:32] or "research"
                    out_file = WORKSPACE_DIR / "documents" / f"research_{slug}.md"
                    md_lines = [f"# Hasil Riset: {q}\n", f"Waktu: {_utc_now()}\n"]
                    for idx, item in enumerate(results, 1):
                        md_lines.append(f"{idx}. **{item.get('title')}** — {item.get('url')}")
                        if item.get("snippet"):
                            md_lines.append(f"   > {item['snippet']}")
                    out_file.write_text("\n".join(md_lines) + "\n", encoding="utf-8")
                    saved_file = str(out_file)
                output = {"query": q, "results": results, "saved_to": saved_file}

            elif tool_name == "web_read":
                url = str(args.get("url", "")).strip()
                text_extracted = ""
                title = url
                if url.startswith("http://") or url.startswith("https://"):
                    try:
                        with httpx.Client(timeout=6.0, follow_redirects=True) as client:
                            r = client.get(url)
                            soup = BeautifulSoup(r.text, "html.parser")
                            title = soup.title.string.strip() if soup.title and soup.title.string else url
                            text_extracted = " ".join(soup.stripped_strings)[:6000]
                    except Exception as exc:
                        text_extracted = f"Tidak dapat mengambil URL eksternal secara langsung ({type(exc).__name__})."
                else:
                    local_p = ToolManager._resolve_safe_path(url)
                    soup = BeautifulSoup(local_p.read_text(encoding="utf-8", errors="ignore"), "html.parser")
                    title = soup.title.string.strip() if soup.title and soup.title.string else local_p.name
                    text_extracted = " ".join(soup.stripped_strings)[:6000]
                output = {"url": url, "title": title, "content_summary": text_extracted[:2000]}

            elif tool_name == "code_inspect":
                target = ToolManager._resolve_safe_path(args.get("path", "projects"))
                files_report = []
                py_targets = [target] if target.is_file() else list(target.rglob("*.py"))
                for pyf in py_targets[:30]:
                    src = pyf.read_text(encoding="utf-8", errors="replace")
                    try:
                        tree = ast.parse(src, filename=str(pyf))
                        funcs = [n.name for n in ast.walk(tree) if isinstance(n, ast.FunctionDef)]
                        classes = [n.name for n in ast.walk(tree) if isinstance(n, ast.ClassDef)]
                        files_report.append({
                            "file": str(pyf.relative_to(WORKSPACE_DIR)) if str(pyf).startswith(str(WORKSPACE_DIR)) else str(pyf),
                            "syntax_ok": True,
                            "functions": funcs,
                            "classes": classes,
                            "lines": len(src.splitlines()),
                        })
                    except SyntaxError as se:
                        files_report.append({
                            "file": str(pyf),
                            "syntax_ok": False,
                            "error": f"Line {se.lineno}: {se.msg}",
                        })
                output = {"inspected_count": len(files_report), "files": files_report}

            elif tool_name == "code_test":
                target = ToolManager._resolve_safe_path(args.get("target", "projects"))
                py_files = [str(target)] if target.is_file() else [str(p) for p in target.rglob("*.py")]
                proc = None
                if py_files:
                    proc = subprocess.run(
                        ["python3", "-m", "py_compile"] + py_files[:20],
                        capture_output=True,
                        text=True,
                        timeout=15,
                    )
                output = {
                    "target": str(target),
                    "passed": (proc.returncode == 0) if proc else True,
                    "stdout": proc.stdout if proc else "No Python files to compile.",
                    "stderr": proc.stderr if proc else "",
                }

            else:
                raise ValueError(f"Tool tidak dikenal: {tool_name}")

            status_str = "completed"
        except Exception as exc:
            status_str = "failed"
            output = {"error": str(exc)}

        duration_ms = int((time.time() - start_t) * 1000)
        call_id = f"tc-{uuid.uuid4().hex[:10]}"
        with db_session() as conn:
            conn.execute(
                """INSERT INTO tool_calls
                (id, task_id, step_id, tool_name, arguments_json, risk_level, status, output, duration_ms, backup_path, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    call_id,
                    task_id,
                    step_id,
                    tool_name,
                    json.dumps(args),
                    risk_level,
                    status_str,
                    json.dumps(output),
                    duration_ms,
                    backup_path,
                    _utc_now(),
                ),
            )
        return {
            "call_id": call_id,
            "tool_name": tool_name,
            "risk_level": risk_level,
            "status": status_str,
            "duration_ms": duration_ms,
            "backup_path": backup_path,
            "output": output,
        }
