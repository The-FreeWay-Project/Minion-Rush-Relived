package de.freeway.mrr.exception;

/** 404 — the addressed resource does not exist (or is not owned). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
