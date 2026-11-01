package com.pos.identityaccess.application.model;

import java.time.Instant;
import java.util.Objects;

public record IdempotencyRecord(
        IdempotencyKey key,
        int responseStatus,
        String responseMetadata,
        Instant expiresAt) {

    public IdempotencyRecord {
        Objects.requireNonNull(key, "key");
        if (responseStatus < 100 || responseStatus > 599) {
            throw new IllegalArgumentException("responseStatus must be an HTTP status");
        }
        if (responseMetadata == null || responseMetadata.isBlank()) {
            throw new IllegalArgumentException("responseMetadata must not be blank");
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
