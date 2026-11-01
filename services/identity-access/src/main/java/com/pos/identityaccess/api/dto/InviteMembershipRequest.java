package com.pos.identityaccess.api.dto;

import com.pos.identityaccess.domain.model.Role;

import java.util.UUID;

public record InviteMembershipRequest(
        String email,
        String displayName,
        String phone,
        Role role,
        UUID branchId) {
}
