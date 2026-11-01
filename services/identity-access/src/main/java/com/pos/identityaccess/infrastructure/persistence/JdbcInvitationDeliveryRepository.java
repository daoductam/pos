package com.pos.identityaccess.infrastructure.persistence;

import com.pos.identityaccess.application.model.InvitationDelivery;
import com.pos.identityaccess.application.port.InvitationDeliveryStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
public class JdbcInvitationDeliveryRepository implements InvitationDeliveryStore {

    private static final Duration CLAIM_LEASE = Duration.ofMinutes(5);

    private static final String INSERT = """
            INSERT INTO invitation_deliveries (
                id, membership_id, provider, status, attempt_count, next_attempt_at,
                last_error_code, provider_user_reference)
            VALUES (:id, :membershipId, 'COGNITO', :status, :attemptCount, :nextAttemptAt,
                    :lastErrorCode, :providerUserReference)
            """;

    private static final String FIND_PENDING = """
            WITH candidates AS (
                SELECT id
                FROM invitation_deliveries
                WHERE status = 'QUEUED'
                  AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
                ORDER BY created_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT :limit
            )
            UPDATE invitation_deliveries AS d
            SET next_attempt_at = :leaseUntil,
                updated_at = now()
            FROM candidates
            WHERE d.id = candidates.id
            RETURNING d.id, d.membership_id, d.status, d.attempt_count,
                      d.next_attempt_at, d.last_error_code, d.provider_user_reference
            """;

    private static final String MARK_SENT = """
            UPDATE invitation_deliveries
            SET status = 'SENT',
                attempt_count = attempt_count + 1,
                next_attempt_at = NULL,
                last_error_code = NULL,
                provider_user_reference = :providerUserReference,
                updated_at = :sentAt
            WHERE id = :id
              AND status = 'QUEUED'
            """;

    private static final String MARK_RETRY = """
            UPDATE invitation_deliveries
            SET status = 'QUEUED',
                attempt_count = attempt_count + 1,
                next_attempt_at = :nextAttemptAt,
                last_error_code = :safeErrorCode,
                updated_at = now()
            WHERE id = :id
              AND status = 'QUEUED'
            """;

    private static final String MARK_FAILED = """
            UPDATE invitation_deliveries
            SET status = 'FAILED',
                attempt_count = attempt_count + 1,
                next_attempt_at = NULL,
                last_error_code = :safeErrorCode,
                updated_at = :failedAt
            WHERE id = :id
              AND status = 'QUEUED'
            """;

    private static final String COUNT_FAILED =
            "SELECT count(*) FROM invitation_deliveries WHERE status = 'FAILED'";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcInvitationDeliveryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public InvitationDelivery save(InvitationDelivery delivery) {
        Objects.requireNonNull(delivery, "delivery");
        jdbc.update(INSERT, new MapSqlParameterSource()
                .addValue("id", delivery.id())
                .addValue("membershipId", delivery.membershipId())
                .addValue("status", delivery.status().name())
                .addValue("attemptCount", delivery.attemptCount())
                .addValue("nextAttemptAt", delivery.nextAttemptAt())
                .addValue("lastErrorCode", delivery.lastErrorCode())
                .addValue("providerUserReference", delivery.providerUserReference()));
        return delivery;
    }

    @Override
    public List<InvitationDelivery> findPending(Instant now, int limit) {
        Objects.requireNonNull(now, "now");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jdbc.query(FIND_PENDING, new MapSqlParameterSource()
                .addValue("now", now)
                .addValue("leaseUntil", now.plus(CLAIM_LEASE))
                .addValue("limit", limit), JdbcRowMappers.INVITATION_DELIVERY);
    }

    @Override
    public boolean markSent(UUID deliveryId, Instant sentAt, String providerUserReference) {
        Objects.requireNonNull(deliveryId, "deliveryId");
        Objects.requireNonNull(sentAt, "sentAt");
        return jdbc.update(MARK_SENT, new MapSqlParameterSource()
                .addValue("id", deliveryId)
                .addValue("sentAt", sentAt)
                .addValue("providerUserReference", providerUserReference)) == 1;
    }

    @Override
    public boolean markRetry(UUID deliveryId, Instant nextAttemptAt, String safeErrorCode) {
        validateFailure(deliveryId, nextAttemptAt, safeErrorCode);
        return jdbc.update(MARK_RETRY, new MapSqlParameterSource()
                .addValue("id", deliveryId)
                .addValue("nextAttemptAt", nextAttemptAt)
                .addValue("safeErrorCode", safeErrorCode)) == 1;
    }

    @Override
    public boolean markFailed(UUID deliveryId, Instant failedAt, String safeErrorCode) {
        Objects.requireNonNull(deliveryId, "deliveryId");
        Objects.requireNonNull(failedAt, "failedAt");
        if (safeErrorCode == null || safeErrorCode.isBlank() || safeErrorCode.length() > 100) {
            throw new IllegalArgumentException("safeErrorCode must contain 1 to 100 characters");
        }
        return jdbc.update(MARK_FAILED, new MapSqlParameterSource()
                .addValue("id", deliveryId)
                .addValue("failedAt", failedAt)
                .addValue("safeErrorCode", safeErrorCode)) == 1;
    }

    @Override
    public long countFailed() {
        Number count = jdbc.getJdbcOperations().queryForObject(COUNT_FAILED, Number.class);
        return count == null ? 0L : count.longValue();
    }

    private static void validateFailure(UUID deliveryId, Instant nextAttemptAt, String safeErrorCode) {
        Objects.requireNonNull(deliveryId, "deliveryId");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        if (safeErrorCode == null || safeErrorCode.isBlank() || safeErrorCode.length() > 100) {
            throw new IllegalArgumentException("safeErrorCode must contain 1 to 100 characters");
        }
    }
}
