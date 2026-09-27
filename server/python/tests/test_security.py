from mrr.security import (
    generate_session_token,
    hash_password,
    hash_token,
    verify_password,
)


def test_hash_password_uses_pbkdf2_prefix() -> None:
    password_hash = hash_password("s3cret-password")
    assert password_hash.startswith("pbkdf2_sha256$")


def test_hash_password_uses_random_salt() -> None:
    assert hash_password("same-password") != hash_password("same-password")


def test_hash_password_never_contains_plaintext() -> None:
    assert "s3cret-password" not in hash_password("s3cret-password")


def test_verify_password_accepts_correct_password() -> None:
    password_hash = hash_password("s3cret-password")
    assert verify_password(password_hash, "s3cret-password") is True


def test_verify_password_rejects_wrong_password() -> None:
    password_hash = hash_password("s3cret-password")
    assert verify_password(password_hash, "wrong-password") is False


def test_verify_password_rejects_malformed_hash() -> None:
    assert verify_password("not-a-hash", "anything") is False
    assert verify_password("", "anything") is False


def test_session_tokens_are_unique_and_random() -> None:
    tokens = {generate_session_token() for _ in range(50)}
    assert len(tokens) == 50
    assert all(len(token) >= 32 for token in tokens)


def test_hash_token_is_sha256_hex() -> None:
    token = generate_session_token()
    digest = hash_token(token)
    assert len(digest) == 64
    assert all(c in "0123456789abcdef" for c in digest)
    assert digest == hash_token(token)  # deterministic
    assert token not in digest
