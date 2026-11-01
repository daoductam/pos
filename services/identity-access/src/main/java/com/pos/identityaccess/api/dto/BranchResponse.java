package com.pos.identityaccess.api.dto;

import com.pos.identityaccess.domain.model.BranchStatus;

import java.time.Instant;
import java.util.UUID;

public record BranchResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String address,
        BranchStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
