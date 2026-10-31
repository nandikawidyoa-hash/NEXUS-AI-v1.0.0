import os
import secrets
from pathlib import Path

BASE_PROJECT_DIR = Path(os.environ.get("NEXUS_ROOT", Path(__file__).resolve().parents[2]))
WORKSPACE_DIR = Path(os.environ.get("NEXUS_WORKSPACE", BASE_PROJECT_DIR / "workspace")).resolve()
SKILLS_DIR = Path(os.environ.get("NEXUS_SKILLS_DIR", BASE_PROJECT_DIR / "skills")).resolve()
DB_PATH = Path(os.environ.get("NEXUS_DB_PATH", BASE_PROJECT_DIR / "engine" / "nexus_data.db")).resolve()
SECRET_KEY_FILE = Path(os.environ.get("NEXUS_SECRET_FILE", BASE_PROJECT_DIR / "engine" / ".nexus_secret.key")).resolve()

HOST = os.environ.get("NEXUS_HOST", "127.0.0.1")
PORT = int(os.environ.get("NEXUS_PORT", "8765"))
DEFAULT_LOCAL_TOKEN = os.environ.get("NEXUS_API_TOKEN", "nexus-local-dev-token-2026")

for sub in ["projects", "documents", "downloads", "backups"]:
    (WORKSPACE_DIR / sub).mkdir(parents=True, exist_ok=True)
SKILLS_DIR.mkdir(parents=True, exist_ok=True)
