package com.pos.identityaccess.api.dto;

import com.pos.identityaccess.domain.model.MembershipStatus;
import com.pos.identityaccess.domain.model.Role;

import java.util.Set;
import java.util.UUID;

public record MembershipResponse(
        UUID id,
        UUID tenantId,
        UUID branchId,
        UUID userId,
        String email,
        Role role,
        MembershipStatus status,
        String invitationStatus,
        Set<String> permissions,
        long version) {

    public MembershipResponse {
        permissions = Set.copyOf(permissions == null ? Set.of() : permissions);
    }
}
