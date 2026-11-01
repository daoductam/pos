package com.pos.identityaccess.application.model;

import com.pos.identityaccess.domain.model.Role;

import java.util.Set;
import java.util.UUID;

public record AuthorizationDecision(
        boolean allowed,
        String reasonCode,
        String subject,
        UUID tenantId,
        UUID branchId,
        Role role,
        Set<String> permissions,
        Long membershipVersion,
        String scopeType) {

    public AuthorizationDecision {
        permissions = Set.copyOf(permissions == null ? Set.of() : permissions);
    }

    public static AuthorizationDecision denied(
            AuthorizationRequest request, String reasonCode) {
        return new AuthorizationDecision(false, reasonCode, request.subject(), request.tenantId(),
                request.branchId(), null, Set.of(), null,
                request.branchId() == null ? "TENANT" : "BRANCH");
    }
}
