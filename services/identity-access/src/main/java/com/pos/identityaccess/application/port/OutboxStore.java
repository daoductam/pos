package com.pos.identityaccess.application.port;

import com.pos.identityaccess.application.model.OutboxEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxStore {

    OutboxEvent save(OutboxEvent event);

    List<OutboxEvent> findPending(Instant now, int limit);

    boolean markPublished(UUID eventId, Instant publishedAt);

    boolean markRetry(UUID eventId, Instant nextAttemptAt, String safeErrorCode);

    boolean markFailed(UUID eventId, Instant failedAt, String safeErrorCode);

    long countFailed();
}
