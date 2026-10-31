import base64
import hashlib
import hmac
import os
import re
import secrets
from nexus.config import SECRET_KEY_FILE

try:
    from cryptography.fernet import Fernet
    HAS_FERNET = True
except ImportError:
    HAS_FERNET = False

SECRET_PATTERNS = [
    re.compile(r"sk-[A-Za-z0-9_\-]{16,}"),
    re.compile(r"AIza[0-9A-Za-z_\-]{20,}"),
    re.compile(r"(?i)(api[_-]?key|password|secret|bearer|token)\s*[:=]\s*['\"]?[A-Za-z0-9_\-\.]{12,}['\"]?"),
]

def _get_or_create_raw_key() -> bytes:
    env_key = os.environ.get("NEXUS_MASTER_KEY")
    if env_key:
        digest = hashlib.sha256(env_key.encode("utf-8")).digest()
        return base64.urlsafe_b64encode(digest)
    if SECRET_KEY_FILE.exists():
        return SECRET_KEY_FILE.read_bytes().strip()
    raw = base64.urlsafe_b64encode(secrets.token_bytes(32))
    SECRET_KEY_FILE.parent.mkdir(parents=True, exist_ok=True)
    SECRET_KEY_FILE.write_bytes(raw)
    try:
        os.chmod(SECRET_KEY_FILE, 0o600)
    except OSError:
        pass
    return raw

def _xor_stream(data: bytes, key: bytes, nonce: bytes) -> bytes:
    out = bytearray(len(data))
    block_Count = (len(data) + 31) // 32
    ks = b"".join(
        hmac.new(key, nonce + i.to_bytes(4, "big"), hashlib.sha256).digest()
        for i in range(block_Count)
    )
    for i in range(len(data)):
        out[i] = data[i] ^ ks[i]
    return bytes(out)

def encrypt_secret(plain_text: str) -> str:
    if not plain_text:
        return ""
    raw_key = _get_or_create_raw_key()
    if HAS_FERNET:
        f = Fernet(raw_key)
        return "fernet:" + f.encrypt(plain_text.encode("utf-8")).decode("utf-8")
    nonce = secrets.token_bytes(16)
    ct = _xor_stream(plain_text.encode("utf-8"), raw_key, nonce)
    mac = hmac.new(raw_key, nonce + ct, hashlib.sha256).digest()
    blob = base64.urlsafe_b64encode(nonce + mac + ct).decode("utf-8")
    return "std:" + blob

def decrypt_secret(cipher_text: str) -> str:
    if not cipher_text:
        return ""
    raw_key = _get_or_create_raw_key()
    if cipher_text.startswith("fernet:") and HAS_FERNET:
        f = Fernet(raw_key)
        return f.decrypt(cipher_text[7:].encode("utf-8")).decode("utf-8")
    if cipher_text.startswith("std:"):
        raw = base64.urlsafe_b64decode(cipher_text[4:].encode("utf-8"))
        nonce, mac, ct = raw[:16], raw[16:48], raw[48:]
        expected = hmac.new(raw_key, nonce + ct, hashlib.sha256).digest()
        if not hmac.compare_digest(mac, expected):
            raise ValueError("Invalid secret HMAC")
        return _xor_stream(ct, raw_key, nonce).decode("utf-8")
    if HAS_FERNET:
        f = Fernet(raw_key)
        return f.decrypt(cipher_text.encode("utf-8")).decode("utf-8")
    return ""

def mask_api_key(api_key: str) -> str:
    if not api_key:
        return "Not configured"
    clean = api_key.strip()
    if len(clean) <= 8:
        return "****" + clean[-2:]
    return f"{clean[:4]}...{clean[-4:]}"

def contains_sensitive_secret(text: str) -> bool:
    if not text:
        return False
    for pattern in SECRET_PATTERNS:
        if pattern.search(text):
            return True
    return False

def redact_secrets(text: str) -> str:
    if not text:
        return ""
    redacted = text
    for pattern in SECRET_PATTERNS:
        redacted = pattern.sub("[REDACTED_SECRET]", redacted)
    return redacted
