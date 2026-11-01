package com.pos.identityaccess.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record User(
        UUID id,
        String cognitoSubject,
        String normalizedEmail,
        String displayName,
        String phone,
        UserStatus status,
        String reconciliationStatus,
        Instant createdAt,
        Instant updatedAt) {

    public User {
        Objects.requireNonNull(id, "id");
        if (normalizedEmail == null || normalizedEmail.isBlank() || normalizedEmail.length() > 320) {
            throw new IllegalArgumentException("normalizedEmail must contain 1 to 320 characters");
        }
        if (displayName == null || displayName.isBlank() || displayName.length() > 200) {
            throw new IllegalArgumentException("displayName must contain 1 to 200 characters");
        }
        Objects.requireNonNull(status, "status");
        if (reconciliationStatus == null || reconciliationStatus.isBlank()) {
            throw new IllegalArgumentException("reconciliationStatus must not be blank");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
