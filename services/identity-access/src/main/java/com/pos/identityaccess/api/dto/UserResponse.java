package com.pos.identityaccess.api.dto;

import com.pos.identityaccess.domain.model.UserStatus;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String phone,
        UserStatus status) {
}
