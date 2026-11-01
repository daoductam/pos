package com.pos.identityaccess.infrastructure.persistence;

import com.pos.identityaccess.application.model.AuthorizationScope;
import com.pos.identityaccess.application.port.TenantRepository;
import com.pos.identityaccess.domain.model.Tenant;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcTenantRepository implements TenantRepository {

    private static final String FIND_BY_ID = """
            SELECT id, name, status, version, created_at, updated_at, created_by, updated_by
            FROM tenants
            WHERE id = :tenantId
              AND :scopeTenantId = :tenantId
            """;

    private static final String INSERT = """
            INSERT INTO tenants (
                id, name, status, version, created_at, updated_at, created_by, updated_by)
            VALUES (
                :id, :name, :status, :version, :createdAt, :updatedAt, :createdBy, :updatedBy)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcTenantRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<Tenant> findById(AuthorizationScope scope, UUID tenantId) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(tenantId, "tenantId");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("scopeTenantId", scope.tenantId());
        return jdbc.query(FIND_BY_ID, parameters, JdbcRowMappers.TENANT).stream().findFirst();
    }

    @Override
    public Tenant save(Tenant tenant) {
        Objects.requireNonNull(tenant, "tenant");
        jdbc.update(INSERT, new MapSqlParameterSource()
                .addValue("id", tenant.id())
                .addValue("name", tenant.name())
                .addValue("status", tenant.status().name())
                .addValue("version", tenant.version())
                .addValue("createdAt", tenant.createdAt())
                .addValue("updatedAt", tenant.updatedAt())
                .addValue("createdBy", tenant.createdBy())
                .addValue("updatedBy", tenant.updatedBy()));
        return tenant;
    }
}
