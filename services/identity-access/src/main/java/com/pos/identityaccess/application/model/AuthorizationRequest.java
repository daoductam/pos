package com.pos.identityaccess.application.model;

import java.util.UUID;

public record AuthorizationRequest(
        String subject,
        UUID tenantId,
        UUID branchId,
        String requiredPermission,
        String requestedResource) {

    public AuthorizationRequest {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (requiredPermission == null || requiredPermission.isBlank()) {
            throw new IllegalArgumentException("requiredPermission must not be blank");
        }
    }
}
