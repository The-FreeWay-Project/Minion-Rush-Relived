package de.freeway.mrr.exception;

/** 409 — the username is already taken. */
public class AccountAlreadyExistsException extends RuntimeException {

    public AccountAlreadyExistsException(String message) {
        super(message);
    }
}
