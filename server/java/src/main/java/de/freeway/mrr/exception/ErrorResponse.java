package de.freeway.mrr.exception;

/**
 * HTTP error payload shaped like FastAPI's: {@code {"detail": "..."}}.
 */
public record ErrorResponse(String detail) {
}
