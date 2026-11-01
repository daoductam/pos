package com.pos.identityaccess.infrastructure.persistence;

import com.pos.identityaccess.application.model.OutboxEvent;
import com.pos.identityaccess.application.port.OutboxStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
public class JdbcOutboxRepository implements OutboxStore {

    private static final Duration CLAIM_LEASE = Duration.ofMinutes(5);

    private static final String INSERT = """
            INSERT INTO outbox_events (
                id, tenant_id, branch_id, event_type, event_version, aggregate_type,
                aggregate_id, payload, correlation_id, causation_id, publish_status,
                attempt_count, created_at)
            VALUES (
                :id, :tenantId, :branchId, :eventType, :eventVersion, :aggregateType,
                :aggregateId, CAST(:payload AS jsonb), :correlationId, :causationId,
                'PENDING', :attemptCount, :occurredAt)
            """;

    private static final String FIND_PENDING = """
            WITH candidates AS (
                SELECT id
                FROM outbox_events
                WHERE publish_status = 'PENDING'
                  AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
                ORDER BY created_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT :limit
            )
            UPDATE outbox_events AS oe
            SET next_attempt_at = :leaseUntil
            FROM candidates
            WHERE oe.id = candidates.id
            RETURNING oe.id, oe.tenant_id, oe.branch_id, oe.event_type,
                      oe.event_version, oe.aggregate_type, oe.aggregate_id,
                      CAST(oe.payload AS text) AS payload, oe.correlation_id,
                      oe.causation_id, oe.created_at, oe.attempt_count
            """;

    private static final String MARK_PUBLISHED = """
            UPDATE outbox_events
            SET publish_status = 'PUBLISHED',
                published_at = :publishedAt,
                next_attempt_at = NULL
            WHERE id = :id
              AND publish_status = 'PENDING'
            """;

    private static final String MARK_RETRY = """
            UPDATE outbox_events
            SET publish_status = 'PENDING',
                last_error_code = :safeErrorCode,
                next_attempt_at = :nextAttemptAt,
                attempt_count = attempt_count + 1
            WHERE id = :id
              AND publish_status = 'PENDING'
            """;

    private static final String MARK_FAILED = """
            UPDATE outbox_events
            SET publish_status = 'FAILED',
                last_error_code = :safeErrorCode,
                next_attempt_at = NULL,
                attempt_count = attempt_count + 1
            WHERE id = :id
              AND publish_status = 'PENDING'
            """;

    private static final String COUNT_FAILED =
            "SELECT count(*) FROM outbox_events WHERE publish_status = 'FAILED'";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOutboxRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public OutboxEvent save(OutboxEvent event) {
        Objects.requireNonNull(event, "event");
        jdbc.update(INSERT, new MapSqlParameterSource()
                .addValue("id", event.id())
                .addValue("tenantId", event.tenantId())
                .addValue("branchId", event.branchId())
                .addValue("eventType", event.eventType())
                .addValue("eventVersion", event.eventVersion())
                .addValue("aggregateType", event.aggregateType())
                .addValue("aggregateId", event.aggregateId())
                .addValue("payload", event.payload())
                .addValue("correlationId", event.correlationId())
                .addValue("causationId", event.causationId())
                .addValue("attemptCount", event.attemptCount())
                .addValue("occurredAt", event.occurredAt()));
        return event;
    }

    @Override
    public List<OutboxEvent> findPending(Instant now, int limit) {
        Objects.requireNonNull(now, "now");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jdbc.query(FIND_PENDING, new MapSqlParameterSource()
                .addValue("now", now)
                .addValue("leaseUntil", now.plus(CLAIM_LEASE))
                .addValue("limit", limit), JdbcRowMappers.OUTBOX_EVENT);
    }

    @Override
    public boolean markPublished(UUID eventId, Instant publishedAt) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(publishedAt, "publishedAt");
        return jdbc.update(MARK_PUBLISHED, new MapSqlParameterSource()
                .addValue("id", eventId)
                .addValue("publishedAt", publishedAt)) == 1;
    }

    @Override
    public boolean markRetry(UUID eventId, Instant nextAttemptAt, String safeErrorCode) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        if (safeErrorCode == null || safeErrorCode.isBlank() || safeErrorCode.length() > 100) {
            throw new IllegalArgumentException("safeErrorCode must contain 1 to 100 characters");
        }
        return jdbc.update(MARK_RETRY, new MapSqlParameterSource()
                .addValue("id", eventId)
                .addValue("nextAttemptAt", nextAttemptAt)
                .addValue("safeErrorCode", safeErrorCode)) == 1;
    }

    @Override
    public boolean markFailed(UUID eventId, Instant failedAt, String safeErrorCode) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(failedAt, "failedAt");
        if (safeErrorCode == null || safeErrorCode.isBlank() || safeErrorCode.length() > 100) {
            throw new IllegalArgumentException("safeErrorCode must contain 1 to 100 characters");
        }
        return jdbc.update(MARK_FAILED, new MapSqlParameterSource()
                .addValue("id", eventId)
                .addValue("safeErrorCode", safeErrorCode)) == 1;
    }

    @Override
    public long countFailed() {
        Number count = jdbc.getJdbcOperations().queryForObject(COUNT_FAILED, Number.class);
        return count == null ? 0L : count.longValue();
    }
}
