import hmac
from typing import Optional
from fastapi import Header, HTTPException, Request, status
from nexus.config import DEFAULT_LOCAL_TOKEN
from nexus.database.db import get_setting

def get_active_token() -> str:
    saved = get_setting("local_api_token", None)
    if saved and isinstance(saved, str) and len(saved) >= 12:
        return saved
    return DEFAULT_LOCAL_TOKEN

def verify_token_string(token: Optional[str]) -> bool:
    if not token:
        return False
    clean = token.strip()
    if clean.lower().startswith("bearer "):
        clean = clean[7:].strip()
    expected = get_active_token()
    return hmac.compare_digest(clean, expected)

async def require_auth(
    request: Request,
    authorization: Optional[str] = Header(default=None),
    x_nexus_token: Optional[str] = Header(default=None),
):
    candidate = authorization or x_nexus_token or request.query_params.get("token")
    if not verify_token_string(candidate):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing NEXUS local authentication token.",
        )
    return True
