package com.pos.identityaccess.application.model;

import java.util.Objects;
import java.util.UUID;

public record CognitoInvitationRequest(UUID deliveryId, String normalizedEmail, String displayName) {

    public CognitoInvitationRequest {
        Objects.requireNonNull(deliveryId, "deliveryId");
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("normalizedEmail must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
    }
}
