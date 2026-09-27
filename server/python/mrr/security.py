"""Security primitives for MRR.

- Passwords: PBKDF2-HMAC-SHA256 (stdlib ``hashlib``), per-password random
  salt, stored as ``pbkdf2_sha256$<iterations>$<salt_hex>$<hash_hex>``.
  Plaintext passwords are never stored or logged.
- Session tokens: cryptographically random (``secrets``), stored **only** as
  a SHA-256 hash in the database.
"""

from __future__ import annotations

import hashlib
import hmac
import secrets

PBKDF2_ITERATIONS = 600_000
PBKDF2_ALGORITHM = "sha256"
PBKDF2_SALT_BYTES = 16
PBKDF2_PREFIX = "pbkdf2_sha256"
_TOKEN_BYTES = 32
TOKEN_HASH_ALGORITHM = "sha256"


def hash_password(password: str) -> str:
    """Hash a password with PBKDF2-HMAC-SHA256 and a fresh random salt."""
    salt = secrets.token_bytes(PBKDF2_SALT_BYTES)
    digest = hashlib.pbkdf2_hmac(
        PBKDF2_ALGORITHM, password.encode("utf-8"), salt, PBKDF2_ITERATIONS
    )
    return f"{PBKDF2_PREFIX}${PBKDF2_ITERATIONS}${salt.hex()}${digest.hex()}"


def verify_password(password_hash: str, password: str) -> bool:
    """Constant-time verification of a password against a stored hash."""
    try:
        prefix, iterations_raw, salt_hex, digest_hex = password_hash.split("$")
        iterations = int(iterations_raw)
        salt = bytes.fromhex(salt_hex)
        expected = bytes.fromhex(digest_hex)
    except (ValueError, AttributeError):
        return False
    if prefix != PBKDF2_PREFIX or iterations <= 0:
        return False
    candidate = hashlib.pbkdf2_hmac(
        PBKDF2_ALGORITHM, password.encode("utf-8"), salt, iterations
    )
    return hmac.compare_digest(candidate, expected)


def generate_session_token() -> str:
    """Create a cryptographically random bearer token."""
    return secrets.token_urlsafe(_TOKEN_BYTES)


def hash_token(token: str) -> str:
    """SHA-256 hash of a session token (only the hash is persisted)."""
    return hashlib.sha256(token.encode("utf-8")).hexdigest()
