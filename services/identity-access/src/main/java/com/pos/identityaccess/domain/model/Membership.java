package com.pos.identityaccess.domain.model;

import java.util.Objects;
import java.util.UUID;

public record Membership(
        UUID id,
        UUID tenantId,
        UUID userId,
        UUID branchId,
        Role role,
        MembershipStatus status,
        long version,
        String createdBy,
        String updatedBy,
        String statusReason) {

    public Membership(
            UUID id,
            UUID tenantId,
            UUID userId,
            UUID branchId,
            Role role,
            MembershipStatus status,
            long version) {
        this(id, tenantId, userId, branchId, role, status, version, "system", "system", null);
    }

    public Membership(
            UUID id,
            UUID tenantId,
            UUID userId,
            UUID branchId,
            Role role,
            MembershipStatus status,
            long version,
            String createdBy,
            String updatedBy) {
        this(id, tenantId, userId, branchId, role, status, version, createdBy, updatedBy, null);
    }

    public Membership {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        if (createdBy == null || createdBy.isBlank()) {
            throw new IllegalArgumentException("createdBy must not be blank");
        }
        if (updatedBy == null || updatedBy.isBlank()) {
            throw new IllegalArgumentException("updatedBy must not be blank");
        }
        if (statusReason != null && statusReason.length() > 500) {
            throw new IllegalArgumentException("statusReason must not exceed 500 characters");
        }
        if (role.requiresBranch() && branchId == null) {
            throw new IllegalArgumentException("non-owner membership requires a branch");
        }
        if (!role.requiresBranch() && branchId != null) {
            throw new IllegalArgumentException("owner membership cannot have a branch");
        }
    }
}
