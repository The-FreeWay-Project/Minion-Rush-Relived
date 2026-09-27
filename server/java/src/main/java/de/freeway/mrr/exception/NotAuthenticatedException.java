package de.freeway.mrr.exception;

/** 401 — missing, invalid or expired session token on a protected route. */
public class NotAuthenticatedException extends RuntimeException {

    public NotAuthenticatedException(String message) {
        super(message);
    }
}
