package com.pos.identityaccess.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OutboxEvent(
        UUID id,
        UUID tenantId,
        UUID branchId,
        String eventType,
        int eventVersion,
        String aggregateType,
        UUID aggregateId,
        String payload,
        String correlationId,
        String causationId,
        Instant occurredAt,
        int attemptCount) {

    public OutboxEvent(
            UUID id,
            UUID tenantId,
            UUID branchId,
            String eventType,
            int eventVersion,
            String aggregateType,
            UUID aggregateId,
            String payload,
            String correlationId,
            String causationId,
            Instant occurredAt) {
        this(id, tenantId, branchId, eventType, eventVersion, aggregateType, aggregateId, payload,
                correlationId, causationId, occurredAt, 0);
    }

    public OutboxEvent {
        Objects.requireNonNull(id, "id");
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be positive");
        }
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalArgumentException("aggregateType must not be blank");
        }
        Objects.requireNonNull(aggregateId, "aggregateId");
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("payload must not be blank");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
    }
}
