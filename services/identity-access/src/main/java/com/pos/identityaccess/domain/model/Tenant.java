package com.pos.identityaccess.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Tenant(
        UUID id,
        String name,
        TenantStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy) {

    public Tenant {
        Objects.requireNonNull(id, "id");
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new IllegalArgumentException("tenant name must contain 1 to 200 characters");
        }
        Objects.requireNonNull(status, "status");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (createdBy == null || createdBy.isBlank()) {
            throw new IllegalArgumentException("createdBy must not be blank");
        }
        if (updatedBy == null || updatedBy.isBlank()) {
            throw new IllegalArgumentException("updatedBy must not be blank");
        }
    }
}
