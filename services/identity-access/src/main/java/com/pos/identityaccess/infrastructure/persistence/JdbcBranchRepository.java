package com.pos.identityaccess.infrastructure.persistence;

import com.pos.identityaccess.application.model.AuthorizationScope;
import com.pos.identityaccess.application.port.BranchRepository;
import com.pos.identityaccess.domain.model.Branch;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcBranchRepository implements BranchRepository {

    private static final String SELECT_COLUMNS = """
            b.id, b.tenant_id, b.code, b.normalized_code, b.name, b.address, b.status,
            b.version, b.created_at, b.updated_at, b.created_by, b.updated_by
            """;

    private static final String INSERT = """
            INSERT INTO branches (
                id, tenant_id, code, normalized_code, name, address, status, version,
                created_at, updated_at, created_by, updated_by)
            VALUES (
                :id, :tenantId, :code, :normalizedCode, :name, :address, :status, :version,
                :createdAt, :updatedAt, :createdBy, :updatedBy)
            """;

    private static final String UPDATE = """
            UPDATE branches
            SET code = :code,
                normalized_code = :normalizedCode,
                name = :name,
                address = :address,
                status = :status,
                version = version + 1,
                updated_at = :updatedAt
            WHERE id = :id
              AND tenant_id = :tenantId
              AND version = :expectedVersion
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcBranchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public List<Branch> findVisibleByTenant(AuthorizationScope scope, UUID tenantId) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(tenantId, "tenantId");
        if (!scope.role().equals(com.pos.identityaccess.domain.model.Role.OWNER)
                && scope.branchIds().isEmpty()) {
            return List.of();
        }

        String sql = "SELECT " + SELECT_COLUMNS + " FROM branches b"
                + " WHERE b.tenant_id = :tenantId"
                + " AND :scopeTenantId = :tenantId"
                + branchPredicate(scope, "b.id")
                + " ORDER BY b.id";
        return jdbc.query(sql, branchParameters(scope, tenantId), JdbcRowMappers.BRANCH);
    }

    @Override
    public Optional<Branch> findVisibleById(AuthorizationScope scope, UUID tenantId, UUID branchId) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(branchId, "branchId");
        if (!scope.role().equals(com.pos.identityaccess.domain.model.Role.OWNER)
                && !scope.branchIds().contains(branchId)) {
            return Optional.empty();
        }

        String sql = "SELECT " + SELECT_COLUMNS + " FROM branches b"
                + " WHERE b.id = :branchId"
                + " AND b.tenant_id = :tenantId"
                + " AND :scopeTenantId = :tenantId";
        MapSqlParameterSource parameters = branchParameters(scope, tenantId)
                .addValue("branchId", branchId);
        return jdbc.query(sql, parameters, JdbcRowMappers.BRANCH).stream().findFirst();
    }

    @Override
    public Optional<Branch> findByTenantAndId(UUID tenantId, UUID branchId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(branchId, "branchId");
        String sql = "SELECT " + SELECT_COLUMNS + " FROM branches b"
                + " WHERE b.tenant_id = :tenantId"
                + " AND b.id = :branchId";
        return jdbc.query(sql, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("branchId", branchId), JdbcRowMappers.BRANCH).stream().findFirst();
    }

    @Override
    public Branch save(Branch branch) {
        Objects.requireNonNull(branch, "branch");
        jdbc.update(INSERT, new MapSqlParameterSource()
                .addValue("id", branch.id())
                .addValue("tenantId", branch.tenantId())
                .addValue("code", branch.code())
                .addValue("normalizedCode", branch.normalizedCode())
                .addValue("name", branch.name())
                .addValue("address", branch.address())
                .addValue("status", branch.status().name())
                .addValue("version", branch.version())
                .addValue("createdAt", branch.createdAt())
                .addValue("updatedAt", branch.updatedAt())
                .addValue("createdBy", branch.createdBy())
                .addValue("updatedBy", branch.updatedBy()));
        return branch;
    }

    @Override
    public boolean updateIfVersionMatches(Branch branch, long expectedVersion) {
        Objects.requireNonNull(branch, "branch");
        int updated = jdbc.update(UPDATE, new MapSqlParameterSource()
                .addValue("id", branch.id())
                .addValue("tenantId", branch.tenantId())
                .addValue("code", branch.code())
                .addValue("normalizedCode", branch.normalizedCode())
                .addValue("name", branch.name())
                .addValue("address", branch.address())
                .addValue("status", branch.status().name())
                .addValue("updatedAt", branch.updatedAt())
                .addValue("expectedVersion", expectedVersion));
        return updated == 1;
    }

    private static String branchPredicate(AuthorizationScope scope, String column) {
        return scope.role().equals(com.pos.identityaccess.domain.model.Role.OWNER)
                ? ""
                : " AND " + column + " IN (:visibleBranchIds)";
    }

    private static MapSqlParameterSource branchParameters(AuthorizationScope scope, UUID tenantId) {
        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("scopeTenantId", scope.tenantId())
                .addValue("visibleBranchIds", scope.branchIds());
    }
}
