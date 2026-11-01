package com.pos.identityaccess.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Branch(
        UUID id,
        UUID tenantId,
        String code,
        String normalizedCode,
        String name,
        String address,
        BranchStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy) {

    public Branch {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        if (code == null || code.isBlank() || code.length() > 100) {
            throw new IllegalArgumentException("branch code must contain 1 to 100 characters");
        }
        if (normalizedCode == null || normalizedCode.isBlank()) {
            throw new IllegalArgumentException("normalizedCode must not be blank");
        }
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new IllegalArgumentException("branch name must contain 1 to 200 characters");
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
