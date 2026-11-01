package com.pos.identityaccess.application.port;

import com.pos.identityaccess.application.model.InvitationDelivery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface InvitationDeliveryStore {

    InvitationDelivery save(InvitationDelivery delivery);

    List<InvitationDelivery> findPending(Instant now, int limit);

    boolean markSent(UUID deliveryId, Instant sentAt, String providerUserReference);

    boolean markRetry(UUID deliveryId, Instant nextAttemptAt, String safeErrorCode);

    boolean markFailed(UUID deliveryId, Instant failedAt, String safeErrorCode);

    long countFailed();
}
