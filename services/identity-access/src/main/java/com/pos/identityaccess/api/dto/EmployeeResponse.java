package com.pos.identityaccess.api.dto;

import com.pos.identityaccess.domain.model.UserStatus;

import java.util.List;
import java.util.UUID;

public record EmployeeResponse(
        UUID userId,
        String email,
        String displayName,
        String phone,
        UserStatus status,
        List<MembershipResponse> memberships) {

    public EmployeeResponse {
        memberships = List.copyOf(memberships);
    }
}
