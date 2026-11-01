package com.pos.identityaccess.api.dto;

import java.util.UUID;

public record AuthorizationDecisionRequest(
        String subject,
        UUID tenantId,
        UUID branchId,
        String requiredPermission,
        String requestedResource) {
}
