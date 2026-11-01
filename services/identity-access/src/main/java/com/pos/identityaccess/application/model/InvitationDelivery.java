package com.pos.identityaccess.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InvitationDelivery(
        UUID id,
        UUID membershipId,
        InvitationDeliveryStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        String lastErrorCode,
        String providerUserReference) {

    public InvitationDelivery {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(membershipId, "membershipId");
        Objects.requireNonNull(status, "status");
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
        if (lastErrorCode != null && (lastErrorCode.isBlank() || lastErrorCode.length() > 100)) {
            throw new IllegalArgumentException("lastErrorCode must contain 1 to 100 characters");
        }
        if (providerUserReference != null && providerUserReference.length() > 200) {
            throw new IllegalArgumentException("providerUserReference must not exceed 200 characters");
        }
    }

    public static InvitationDelivery queued(UUID id, UUID membershipId) {
        return new InvitationDelivery(id, membershipId, InvitationDeliveryStatus.QUEUED,
                0, null, null, null);
    }
}
