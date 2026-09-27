package de.freeway.mrr.exception;

/** 422 — input failed business validation. */
public class ValidationFailedException extends RuntimeException {

    public ValidationFailedException(String message) {
        super(message);
    }
}
