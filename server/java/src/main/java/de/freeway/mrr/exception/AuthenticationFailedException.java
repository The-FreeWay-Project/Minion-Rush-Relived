package de.freeway.mrr.exception;

/** 401 — login failed (unknown user or wrong password, identical message). */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
