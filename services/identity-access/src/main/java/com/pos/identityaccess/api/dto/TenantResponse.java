package com.pos.identityaccess.api.dto;

import com.pos.identityaccess.domain.model.TenantStatus;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String name,
        TenantStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
