package com.pos.identityaccess.infrastructure.persistence;

import com.pos.identityaccess.application.model.IdempotencyKey;
import com.pos.identityaccess.application.model.IdempotencyRecord;
import com.pos.identityaccess.application.model.InvitationDelivery;
import com.pos.identityaccess.application.model.InvitationDeliveryStatus;
import com.pos.identityaccess.application.model.OutboxEvent;
import com.pos.identityaccess.domain.model.Branch;
import com.pos.identityaccess.domain.model.BranchStatus;
import com.pos.identityaccess.domain.model.Membership;
import com.pos.identityaccess.domain.model.MembershipStatus;
import com.pos.identityaccess.domain.model.Role;
import com.pos.identityaccess.domain.model.Tenant;
import com.pos.identityaccess.domain.model.TenantStatus;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

final class JdbcRowMappers {

    private JdbcRowMappers() {
    }

    static final RowMapper<Tenant> TENANT = (resultSet, rowNum) -> new Tenant(
            resultSet.getObject("id", UUID.class),
            resultSet.getString("name"),
            TenantStatus.valueOf(resultSet.getString("status")),
            resultSet.getLong("version"),
            instant(resultSet, "created_at"),
            instant(resultSet, "updated_at"),
            resultSet.getString("created_by"),
            resultSet.getString("updated_by"));

    static final RowMapper<Branch> BRANCH = (resultSet, rowNum) -> new Branch(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("tenant_id", UUID.class),
            resultSet.getString("code"),
            resultSet.getString("normalized_code"),
            resultSet.getString("name"),
            resultSet.getString("address"),
            BranchStatus.valueOf(resultSet.getString("status")),
            resultSet.getLong("version"),
            instant(resultSet, "created_at"),
            instant(resultSet, "updated_at"),
            resultSet.getString("created_by"),
            resultSet.getString("updated_by"));

    static final RowMapper<Membership> MEMBERSHIP = (resultSet, rowNum) -> new Membership(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("tenant_id", UUID.class),
            resultSet.getObject("user_id", UUID.class),
            resultSet.getObject("branch_id", UUID.class),
            Role.valueOf(resultSet.getString("role")),
            MembershipStatus.valueOf(resultSet.getString("status")),
            resultSet.getLong("version"),
            resultSet.getString("created_by"),
            resultSet.getString("updated_by"),
            resultSet.getString("status_reason"));

    static final RowMapper<IdempotencyRecord> IDEMPOTENCY_RECORD = (resultSet, rowNum) -> new IdempotencyRecord(
            new IdempotencyKey(
                    resultSet.getObject("tenant_id", UUID.class),
                    resultSet.getString("actor_subject"),
                    resultSet.getString("idempotency_key"),
                    resultSet.getString("request_hash")),
            resultSet.getInt("response_status"),
            resultSet.getString("response_metadata"),
            instant(resultSet, "expires_at"));

    static final RowMapper<OutboxEvent> OUTBOX_EVENT = (resultSet, rowNum) -> new OutboxEvent(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("tenant_id", UUID.class),
            resultSet.getObject("branch_id", UUID.class),
            resultSet.getString("event_type"),
            resultSet.getInt("event_version"),
            resultSet.getString("aggregate_type"),
            resultSet.getObject("aggregate_id", UUID.class),
            resultSet.getString("payload"),
            resultSet.getString("correlation_id"),
            resultSet.getString("causation_id"),
            instant(resultSet, "created_at"),
            resultSet.getInt("attempt_count"));

    static final RowMapper<InvitationDelivery> INVITATION_DELIVERY = (resultSet, rowNum) ->
            new InvitationDelivery(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("membership_id", UUID.class),
                    InvitationDeliveryStatus.valueOf(resultSet.getString("status")),
                    resultSet.getInt("attempt_count"),
                    instant(resultSet, "next_attempt_at"),
                    resultSet.getString("last_error_code"),
                    resultSet.getString("provider_user_reference"));

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        java.sql.Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
