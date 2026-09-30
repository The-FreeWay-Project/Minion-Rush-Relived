package de.freeway.mrr.admin.dto;

public record CommandResponseDto(
        boolean success,
        String message,
        String output,
        long executionTimeMs
) {
    public CommandResponseDto(boolean success, String message) {
        this(success, message, message, 0);
    }
}
