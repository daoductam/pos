package com.pos.identityaccess.observability;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SecurityEventFactory {

    public SecurityEvent create(
            String eventType,
            String outcome,
            String actorSubject,
            UUID tenantId,
            UUID branchId,
            String target,
            String correlationId,
            Map<String, ?> metadata) {
        return create(eventType, outcome, actorSubject, actorSubject, tenantId, branchId, target,
                correlationId, metadata);
    }

    public SecurityEvent create(
            String eventType,
            String outcome,
            String actorSubject,
            String effectivePrincipal,
            UUID tenantId,
            UUID branchId,
            String target,
            String correlationId,
            Map<String, ?> metadata) {
        requireText(eventType, "eventType");
        requireText(outcome, "outcome");
        requireText(actorSubject, "actorSubject");
        requireText(effectivePrincipal, "effectivePrincipal");
        requireText(correlationId, "correlationId");
        return new SecurityEvent(eventType, outcome, actorSubject, effectivePrincipal, tenantId,
                branchId, target, correlationId,
                RedactionPolicy.redact(metadata == null ? Map.of() : metadata));
    }

    public record SecurityEvent(
            String eventType,
            String outcome,
            String actorSubject,
            String effectivePrincipal,
            UUID tenantId,
            UUID branchId,
            String target,
            String correlationId,
            Map<String, Object> metadata) {

        public SecurityEvent(
                String eventType,
                String outcome,
                String actorSubject,
                UUID tenantId,
                UUID branchId,
                String target,
                String correlationId,
                Map<String, Object> metadata) {
            this(eventType, outcome, actorSubject, actorSubject, tenantId, branchId, target,
                    correlationId, metadata);
        }
    }

    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
