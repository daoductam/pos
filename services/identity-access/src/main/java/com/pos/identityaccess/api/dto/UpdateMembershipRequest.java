package com.pos.identityaccess.api.dto;

import com.pos.identityaccess.domain.model.Role;

import java.util.UUID;

public record UpdateMembershipRequest(Role role, UUID branchId) {
}
