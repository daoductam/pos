package com.pos.identityaccess.application.model;

public record IdempotencyCommand(String key, String requestHash) {

    public IdempotencyCommand {
        if (key == null || key.isBlank() || key.length() > 200) {
            throw new IllegalArgumentException("idempotency key must contain 1 to 200 characters");
        }
        if (requestHash == null || requestHash.isBlank()) {
            throw new IllegalArgumentException("requestHash must not be blank");
        }
    }
}
