package com.pos.identityaccess.infrastructure.persistence;

import com.pos.identityaccess.application.model.AuthorizationScope;
import com.pos.identityaccess.application.port.MembershipRepository;
import com.pos.identityaccess.domain.model.Membership;
import com.pos.identityaccess.domain.model.MembershipStatus;
import com.pos.identityaccess.domain.model.Role;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcMembershipRepository implements MembershipRepository {

    private static final String SELECT_COLUMNS = """
            m.id, m.tenant_id, m.user_id, m.branch_id, m.role, m.status, m.version,
            m.created_by, m.updated_by, m.status_reason
            """;

    private static final String INSERT = """
            INSERT INTO memberships (
                id, tenant_id, user_id, branch_id, role, status, version, created_by, updated_by, status_reason)
            VALUES (
                :id, :tenantId, :userId, :branchId, :role, :status, :version, :createdBy, :updatedBy, :statusReason)
            """;

    private static final String UPDATE = """
            UPDATE memberships
            SET branch_id = :branchId,
                role = :role,
                status = :status,
                status_reason = :statusReason,
                updated_by = :updatedBy,
                version = version + 1,
                updated_at = now()
            WHERE id = :id
              AND tenant_id = :tenantId
              AND version = :expectedVersion
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMembershipRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public List<Membership> findVisibleBySubject(AuthorizationScope scope, UUID tenantId) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(tenantId, "tenantId");
        if (!isOwner(scope) && scope.branchIds().isEmpty()) {
            return List.of();
        }

        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM memberships m JOIN users u ON u.id = m.user_id"
                + " WHERE m.tenant_id = :tenantId"
                + " AND :scopeTenantId = :tenantId"
                + " AND u.cognito_subject = :subject"
                + membershipBranchPredicate(scope, "m.branch_id")
                + " ORDER BY m.id";
        return jdbc.query(sql, scopeParameters(scope, tenantId), JdbcRowMappers.MEMBERSHIP);
    }

    @Override
    public List<Membership> findActiveBySubjectAndTenant(String subject, UUID tenantId) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(tenantId, "tenantId");
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM memberships m JOIN users u ON u.id = m.user_id"
                + " WHERE m.tenant_id = :tenantId"
                + " AND u.cognito_subject = :subject"
                + " AND m.status = :status"
                + " ORDER BY m.branch_id NULLS FIRST, m.id";
        return jdbc.query(sql, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("subject", subject)
                .addValue("status", MembershipStatus.ACTIVE.name()), JdbcRowMappers.MEMBERSHIP);
    }

    @Override
    public List<Membership> findActiveBySubject(String subject) {
        Objects.requireNonNull(subject, "subject");
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM memberships m JOIN users u ON u.id = m.user_id"
                + " WHERE u.cognito_subject = :subject"
                + " AND m.status = :status"
                + " ORDER BY m.tenant_id, m.branch_id NULLS FIRST, m.id";
        return jdbc.query(sql, new MapSqlParameterSource()
                .addValue("subject", subject)
                .addValue("status", MembershipStatus.ACTIVE.name()), JdbcRowMappers.MEMBERSHIP);
    }

    @Override
    public List<Membership> findVisibleByTenant(AuthorizationScope scope, UUID tenantId) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(tenantId, "tenantId");
        if (!isOwner(scope) && scope.branchIds().isEmpty()) {
            return List.of();
        }
        String sql = "SELECT " + SELECT_COLUMNS + " FROM memberships m"
                + " WHERE m.tenant_id = :tenantId"
                + " AND :scopeTenantId = :tenantId"
                + membershipBranchPredicate(scope, "m.branch_id")
                + " ORDER BY m.id";
        return jdbc.query(sql, scopeParameters(scope, tenantId), JdbcRowMappers.MEMBERSHIP);
    }

    @Override
    public Optional<Membership> findVisibleById(
            AuthorizationScope scope, UUID tenantId, UUID membershipId) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(membershipId, "membershipId");
        if (!isOwner(scope) && scope.branchIds().isEmpty()) {
            return Optional.empty();
        }

        String sql = "SELECT " + SELECT_COLUMNS + " FROM memberships m"
                + " WHERE m.id = :membershipId"
                + " AND m.tenant_id = :tenantId"
                + " AND :scopeTenantId = :tenantId"
                + membershipBranchPredicate(scope, "m.branch_id");
        MapSqlParameterSource parameters = scopeParameters(scope, tenantId)
                .addValue("membershipId", membershipId);
        return jdbc.query(sql, parameters, JdbcRowMappers.MEMBERSHIP).stream().findFirst();
    }

    @Override
    public Optional<Membership> findById(UUID membershipId) {
        Objects.requireNonNull(membershipId, "membershipId");
        return jdbc.query("SELECT " + SELECT_COLUMNS + " FROM memberships m"
                        + " WHERE m.id = :membershipId",
                new MapSqlParameterSource().addValue("membershipId", membershipId),
                JdbcRowMappers.MEMBERSHIP).stream().findFirst();
    }

    @Override
    public Optional<Membership> findActiveByUserAndBranch(UUID tenantId, UUID userId, UUID branchId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(branchId, "branchId");
        String sql = "SELECT " + SELECT_COLUMNS + " FROM memberships m"
                + " WHERE m.tenant_id = :tenantId"
                + " AND m.user_id = :userId"
                + " AND m.branch_id = :branchId"
                + " AND m.status = :status";
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("userId", userId)
                .addValue("branchId", branchId)
                .addValue("status", MembershipStatus.ACTIVE.name());
        return jdbc.query(sql, parameters, JdbcRowMappers.MEMBERSHIP).stream().findFirst();
    }

    @Override
    public Optional<Membership> findActiveTenantOwner(UUID tenantId, UUID userId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        String sql = "SELECT " + SELECT_COLUMNS + " FROM memberships m"
                + " WHERE m.tenant_id = :tenantId"
                + " AND m.user_id = :userId"
                + " AND m.branch_id IS NULL"
                + " AND m.role = :role"
                + " AND m.status = :status";
        return jdbc.query(sql, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("userId", userId)
                .addValue("role", Role.OWNER.name())
                .addValue("status", MembershipStatus.ACTIVE.name()), JdbcRowMappers.MEMBERSHIP)
                .stream().findFirst();
    }

    @Override
    public Optional<Membership> findCurrentByUserAndBranch(UUID tenantId, UUID userId, UUID branchId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(branchId, "branchId");
        String sql = "SELECT " + SELECT_COLUMNS + " FROM memberships m"
                + " WHERE m.tenant_id = :tenantId"
                + " AND m.user_id = :userId"
                + " AND m.branch_id = :branchId"
                + " AND m.status IN ('INVITED', 'ACTIVE', 'SUSPENDED')";
        return jdbc.query(sql, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("userId", userId)
                .addValue("branchId", branchId), JdbcRowMappers.MEMBERSHIP)
                .stream().findFirst();
    }

    @Override
    public Optional<Membership> findCurrentTenantOwner(UUID tenantId, UUID userId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        String sql = "SELECT " + SELECT_COLUMNS + " FROM memberships m"
                + " WHERE m.tenant_id = :tenantId"
                + " AND m.user_id = :userId"
                + " AND m.branch_id IS NULL"
                + " AND m.role = 'OWNER'"
                + " AND m.status IN ('INVITED', 'ACTIVE', 'SUSPENDED')";
        return jdbc.query(sql, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("userId", userId), JdbcRowMappers.MEMBERSHIP)
                .stream().findFirst();
    }

    @Override
    public List<Membership> findCurrentByUser(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return jdbc.query("SELECT " + SELECT_COLUMNS + " FROM memberships m"
                        + " WHERE m.user_id = :userId"
                        + " AND m.status = 'INVITED'"
                        + " ORDER BY m.tenant_id, m.branch_id NULLS FIRST, m.id",
                new MapSqlParameterSource().addValue("userId", userId),
                JdbcRowMappers.MEMBERSHIP);
    }

    @Override
    public long countActiveOwners(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        String sql = "SELECT count(*) FROM memberships"
                + " WHERE tenant_id = :tenantId"
                + " AND branch_id IS NULL"
                + " AND role = :role"
                + " AND status = :status";
        Number count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("role", Role.OWNER.name())
                .addValue("status", MembershipStatus.ACTIVE.name()), Number.class);
        return count == null ? 0L : count.longValue();
    }

    @Override
    public Membership save(Membership membership) {
        Objects.requireNonNull(membership, "membership");
        jdbc.update(INSERT, membershipParameters(membership));
        return membership;
    }

    @Override
    public boolean updateIfVersionMatches(Membership membership, long expectedVersion) {
        Objects.requireNonNull(membership, "membership");
        int updated = jdbc.update(UPDATE, membershipParameters(membership)
                .addValue("expectedVersion", expectedVersion));
        return updated == 1;
    }

    private static boolean isOwner(AuthorizationScope scope) {
        return scope.role() == Role.OWNER;
    }

    private static String membershipBranchPredicate(AuthorizationScope scope, String column) {
        return isOwner(scope) ? "" : " AND " + column + " IN (:visibleBranchIds)";
    }

    private static MapSqlParameterSource scopeParameters(AuthorizationScope scope, UUID tenantId) {
        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("scopeTenantId", scope.tenantId())
                .addValue("subject", scope.subject())
                .addValue("visibleBranchIds", scope.branchIds());
    }

    private static MapSqlParameterSource membershipParameters(Membership membership) {
        return new MapSqlParameterSource()
                .addValue("id", membership.id())
                .addValue("tenantId", membership.tenantId())
                .addValue("userId", membership.userId())
                .addValue("branchId", membership.branchId())
                .addValue("role", membership.role().name())
                .addValue("status", membership.status().name())
                .addValue("version", membership.version())
                .addValue("createdBy", membership.createdBy())
                .addValue("updatedBy", membership.updatedBy())
                .addValue("statusReason", membership.statusReason());
    }
}
