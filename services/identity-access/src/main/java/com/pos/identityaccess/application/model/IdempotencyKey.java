package com.pos.identityaccess.application.model;

import java.util.UUID;

public record IdempotencyKey(UUID tenantId, String actorSubject, String key, String requestHash) {

    public IdempotencyKey {
        if (actorSubject == null || actorSubject.isBlank()) {
            throw new IllegalArgumentException("actorSubject must not be blank");
        }
        if (key == null || key.isBlank() || key.length() > 200) {
            throw new IllegalArgumentException("idempotency key must contain 1 to 200 characters");
        }
        if (requestHash == null || requestHash.isBlank()) {
            throw new IllegalArgumentException("requestHash must not be blank");
        }
    }
}
