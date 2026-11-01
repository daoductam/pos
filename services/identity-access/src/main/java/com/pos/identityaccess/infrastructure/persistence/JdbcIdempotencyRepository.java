package com.pos.identityaccess.infrastructure.persistence;

import com.pos.identityaccess.application.model.IdempotencyKey;
import com.pos.identityaccess.application.model.IdempotencyRecord;
import com.pos.identityaccess.application.IdGenerator;
import com.pos.identityaccess.application.port.IdempotencyStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcIdempotencyRepository implements IdempotencyStore {

    private static final String SELECT = """
            SELECT tenant_id, actor_subject, idempotency_key, request_hash, response_status,
                   CAST(response_metadata AS text) AS response_metadata, expires_at
            FROM idempotency_records
            WHERE ((tenant_id = :tenantId)
                OR (tenant_id IS NULL AND :tenantId IS NULL))
              AND actor_subject = :actorSubject
              AND idempotency_key = :idempotencyKey
            """;

    private static final String INSERT = """
            INSERT INTO idempotency_records (
                id, tenant_id, actor_subject, idempotency_key, request_hash,
                response_status, response_metadata, expires_at)
            VALUES (
                :id, :tenantId, :actorSubject, :idempotencyKey, :requestHash,
                :responseStatus, CAST(:responseMetadata AS jsonb), :expiresAt)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcIdempotencyRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<IdempotencyRecord> find(IdempotencyKey key) {
        Objects.requireNonNull(key, "key");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", key.tenantId())
                .addValue("actorSubject", key.actorSubject())
                .addValue("idempotencyKey", key.key());
        return jdbc.query(SELECT, parameters, JdbcRowMappers.IDEMPOTENCY_RECORD)
                .stream()
                .findFirst();
    }

    @Override
    public IdempotencyRecord save(IdempotencyRecord record) {
        Objects.requireNonNull(record, "record");
        IdempotencyKey key = record.key();
        jdbc.update(INSERT, new MapSqlParameterSource()
                .addValue("id", IdGenerator.uuidV7())
                .addValue("tenantId", key.tenantId())
                .addValue("actorSubject", key.actorSubject())
                .addValue("idempotencyKey", key.key())
                .addValue("requestHash", key.requestHash())
                .addValue("responseStatus", record.responseStatus())
                .addValue("responseMetadata", record.responseMetadata())
                .addValue("expiresAt", record.expiresAt()));
        return record;
    }
}
