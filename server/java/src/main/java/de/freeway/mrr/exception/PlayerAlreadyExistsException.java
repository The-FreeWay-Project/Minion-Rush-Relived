package de.freeway.mrr.exception;

/** 409 — the player_id is already taken. */
public class PlayerAlreadyExistsException extends RuntimeException {

    public PlayerAlreadyExistsException(String message) {
        super(message);
    }
}
