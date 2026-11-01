package com.pos.identityaccess.api.dto;

import java.time.Instant;
import java.util.UUID;

public record InvitationDeliveryResponse(
        UUID deliveryId,
        UUID membershipId,
        String status,
        int attemptCount,
        Instant nextAttemptAt,
        String lastErrorCode) {
}
