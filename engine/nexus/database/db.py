import json
import sqlite3
import threading
import time
import uuid
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Dict, List, Optional
from nexus.config import DB_PATH, WORKSPACE_DIR

_db_lock = threading.RLock()

def _utc_now() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())

def get_connection(db_path: Optional[Path] = None) -> sqlite3.Connection:
    target = str(db_path or DB_PATH)
    conn = sqlite3.connect(target, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA locking_mode=EXCLUSIVE;")
    conn.execute("PRAGMA journal_mode=OFF;")
    conn.execute("PRAGMA synchronous=OFF;")
    conn.execute("PRAGMA temp_store=MEMORY;")
    conn.execute("PRAGMA foreign_keys=ON;")
    return conn

@contextmanager
def db_session(db_path: Optional[Path] = None):
    with _db_lock:
        conn = get_connection(db_path)
        try:
            yield conn
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()

SCHEMA_SQL = """
CREATE TABLE IF NOT EXISTS conversations (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    project_id TEXT,
    runtime TEXT NOT NULL DEFAULT 'local',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS messages (
    id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    model_used TEXT,
    provider_id TEXT,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS memories (
    id TEXT PRIMARY KEY,
    category TEXT NOT NULL,
    project_id TEXT,
    key TEXT NOT NULL,
    content TEXT NOT NULL,
    importance INTEGER NOT NULL DEFAULT 3,
    synced_to_cloud INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE VIRTUAL TABLE IF NOT EXISTS memories_fts USING fts5(
    memory_id UNINDEXED,
    category,
    key,
    content
);

CREATE TABLE IF NOT EXISTS projects (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    workspace_path TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS tasks (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    prompt TEXT NOT NULL,
    conversation_id TEXT,
    project_id TEXT,
    runtime TEXT NOT NULL DEFAULT 'local',
    status TEXT NOT NULL DEFAULT 'pending',
    risk_level TEXT NOT NULL DEFAULT 'low',
    model_id TEXT,
    plan_json TEXT NOT NULL DEFAULT '[]',
    result_summary TEXT,
    error_message TEXT,
    recovery_steps TEXT,
    synced_to_cloud INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS task_steps (
    id TEXT PRIMARY KEY,
    task_id TEXT NOT NULL,
    step_number INTEGER NOT NULL,
    phase TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    tool_name TEXT,
    risk_level TEXT NOT NULL DEFAULT 'low',
    status TEXT NOT NULL DEFAULT 'pending',
    output TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS tool_calls (
    id TEXT PRIMARY KEY,
    task_id TEXT,
    step_id TEXT,
    tool_name TEXT NOT NULL,
    arguments_json TEXT NOT NULL DEFAULT '{}',
    risk_level TEXT NOT NULL DEFAULT 'low',
    status TEXT NOT NULL DEFAULT 'pending',
    output TEXT,
    duration_ms INTEGER NOT NULL DEFAULT 0,
    backup_path TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS providers (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    provider_type TEXT NOT NULL,
    base_url TEXT NOT NULL,
    encrypted_api_key TEXT NOT NULL DEFAULT '',
    key_masked TEXT NOT NULL DEFAULT 'Not configured',
    enabled INTEGER NOT NULL DEFAULT 1,
    priority INTEGER NOT NULL DEFAULT 10,
    rate_limit_rpm INTEGER NOT NULL DEFAULT 60,
    usage_count INTEGER NOT NULL DEFAULT 0,
    last_tested_at TEXT,
    last_test_status TEXT DEFAULT 'untested',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS models (
    id TEXT PRIMARY KEY,
    provider_id TEXT NOT NULL,
    model_name TEXT NOT NULL,
    display_name TEXT NOT NULL,
    capabilities_json TEXT NOT NULL DEFAULT '["chat","planning","coding","tools"]',
    context_window INTEGER NOT NULL DEFAULT 128000,
    is_default INTEGER NOT NULL DEFAULT 0,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS skills (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL,
    version TEXT NOT NULL DEFAULT '1.0.0',
    folder_path TEXT NOT NULL,
    instructions TEXT NOT NULL,
    required_tools_json TEXT NOT NULL DEFAULT '[]',
    required_permissions_json TEXT NOT NULL DEFAULT '[]',
    enabled INTEGER NOT NULL DEFAULT 1,
    is_builtin INTEGER NOT NULL DEFAULT 0,
    security_status TEXT NOT NULL DEFAULT 'verified',
    security_notes TEXT NOT NULL DEFAULT '',
    installed_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS automations (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    prompt TEXT NOT NULL,
    schedule_cron TEXT NOT NULL DEFAULT '0 * * * *',
    interval_seconds INTEGER NOT NULL DEFAULT 3600,
    runtime TEXT NOT NULL DEFAULT 'local',
    enabled INTEGER NOT NULL DEFAULT 1,
    last_run_at TEXT,
    next_run_at TEXT,
    last_status TEXT DEFAULT 'idle',
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS approvals (
    id TEXT PRIMARY KEY,
    task_id TEXT,
    step_id TEXT,
    action_type TEXT NOT NULL,
    risk_level TEXT NOT NULL,
    description TEXT NOT NULL,
    payload_json TEXT NOT NULL DEFAULT '{}',
    status TEXT NOT NULL DEFAULT 'pending',
    requested_at TEXT NOT NULL,
    resolved_at TEXT,
    resolved_by_note TEXT
);

CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value_json TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS activity_logs (
    id TEXT PRIMARY KEY,
    level TEXT NOT NULL,
    category TEXT NOT NULL,
    action TEXT NOT NULL,
    details_json TEXT NOT NULL DEFAULT '{}',
    runtime TEXT NOT NULL DEFAULT 'local',
    created_at TEXT NOT NULL
);
"""

DEFAULT_PROVIDERS = [
    ("prov-gemini", "Google Gemini", "gemini", "https://generativelanguage.googleapis.com/v1beta", 1, 60),
    ("prov-qwen", "Alibaba Qwen (DashScope)", "qwen", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", 2, 60),
    ("prov-deepseek", "DeepSeek API", "deepseek", "https://api.deepseek.com/v1", 3, 60),
    ("prov-openai", "OpenAI API", "openai", "https://api.openai.com/v1", 4, 60),
    ("prov-custom", "Custom OpenAI-Compatible", "custom", "http://127.0.0.1:11434/v1", 5, 120),
]

DEFAULT_MODELS = [
    ("mod-gemini-2.5-flash", "prov-gemini", "gemini-2.5-flash", "Gemini 2.5 Flash (Fast & Agentic)", '["chat","planning","coding","tools"]', 1000000, 1),
    ("mod-gemini-2.5-pro", "prov-gemini", "gemini-2.5-pro", "Gemini 2.5 Pro (Deep Reasoning)", '["chat","planning","coding","tools","research"]', 2000000, 0),
    ("mod-qwen-max", "prov-qwen", "qwen-max", "Qwen Max", '["chat","planning","coding","tools"]', 128000, 0),
    ("mod-deepseek-chat", "prov-deepseek", "deepseek-chat", "DeepSeek V3 Chat", '["chat","planning","coding","tools"]', 128000, 0),
    ("mod-deepseek-reasoner", "prov-deepseek", "deepseek-reasoner", "DeepSeek R1 Reasoner", '["chat","planning","coding","research"]', 128000, 0),
    ("mod-gpt-4o-mini", "prov-openai", "gpt-4o-mini", "GPT-4o Mini", '["chat","planning","coding","tools"]', 128000, 0),
    ("mod-custom-local", "prov-custom", "qwen2.5-coder:7b", "Custom Local/Cloud Model", '["chat","coding","tools"]', 32768, 0),
]

def init_db(db_path: Optional[Path] = None):
    now = _utc_now()
    with db_session(db_path) as conn:
        conn.executescript(SCHEMA_SQL)
        for pid, name, ptype, url, prio, rpm in DEFAULT_PROVIDERS:
            conn.execute(
                """INSERT OR IGNORE INTO providers
                (id, name, provider_type, base_url, encrypted_api_key, key_masked, enabled, priority, rate_limit_rpm, usage_count, created_at, updated_at)
                VALUES (?, ?, ?, ?, '', 'Not configured', 1, ?, ?, 0, ?, ?)""",
                (pid, name, ptype, url, prio, rpm, now, now),
            )
        for mid, pid, mname, dname, caps, ctx, is_def in DEFAULT_MODELS:
            conn.execute(
                """INSERT OR IGNORE INTO models
                (id, provider_id, model_name, display_name, capabilities_json, context_window, is_default, enabled, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?)""",
                (mid, pid, mname, dname, caps, ctx, is_def, now),
            )
        conn.execute(
            """INSERT OR IGNORE INTO projects
            (id, name, description, workspace_path, status, created_at, updated_at)
            VALUES ('proj-default', 'Default Workspace Project', 'Workspace utama NEXUS AI di perangkat Android', ?, 'active', ?, ?)""",
            (str(WORKSPACE_DIR / "projects"), now, now),
        )
        default_settings = {
            "auto_model_routing": True,
            "active_runtime": "local",
            "cloud_runtime_url": "",
            "cloud_runtime_enabled": False,
            "cloud_sync_enabled": False,
            "memory_store_conversations": True,
            "memory_store_tasks": True,
            "agent_stopped": False,
            "allowed_directories": [
                str(WORKSPACE_DIR / "projects"),
                str(WORKSPACE_DIR / "documents"),
                str(WORKSPACE_DIR / "downloads"),
                str(WORKSPACE_DIR / "backups"),
            ],
        }
        for k, v in default_settings.items():
            conn.execute(
                "INSERT OR IGNORE INTO settings (key, value_json, updated_at) VALUES (?, ?, ?)",
                (k, json.dumps(v), now),
            )

def get_setting(key: str, default: Any = None, db_path: Optional[Path] = None) -> Any:
    try:
        with db_session(db_path) as conn:
            row = conn.execute("SELECT value_json FROM settings WHERE key = ?", (key,)).fetchone()
            if row:
                return json.loads(row["value_json"])
    except Exception:
        pass
    return default

def set_setting(key: str, value: Any, db_path: Optional[Path] = None):
    now = _utc_now()
    with db_session(db_path) as conn:
        conn.execute(
            "INSERT INTO settings (key, value_json, updated_at) VALUES (?, ?, ?) ON CONFLICT(key) DO UPDATE SET value_json=excluded.value_json, updated_at=excluded.updated_at",
            (key, json.dumps(value), now),
        )

def log_activity(level: str, category: str, action: str, details: Optional[Dict[str, Any]] = None, runtime: str = "local", db_path: Optional[Path] = None):
    now = _utc_now()
    with db_session(db_path) as conn:
        conn.execute(
            "INSERT INTO activity_logs (id, level, category, action, details_json, runtime, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            (f"log-{uuid.uuid4().hex[:12]}", level, category, action, json.dumps(details or {}), runtime, now),
        )
