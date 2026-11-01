package com.pos.identityaccess.infrastructure.persistence;

import com.pos.identityaccess.application.model.AuthorizationScope;
import com.pos.identityaccess.domain.model.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class JdbcBranchRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void shouldReturnNoRowsForForeignTenantAndKeepTenantPredicate() {
        JdbcBranchRepository repository = new JdbcBranchRepository(jdbc);
        UUID visibleTenant = UUID.randomUUID();
        UUID foreignTenant = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        AuthorizationScope scope = new AuthorizationScope(
                "owner-sub", visibleTenant, Role.OWNER, java.util.Set.of());
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        assertThat(repository.findVisibleById(scope, foreignTenant, branchId)).isEmpty();
        verify(jdbc).query(
                org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("b.tenant_id = :tenantId")
                        && sql.contains(":scopeTenantId = :tenantId")),
                any(SqlParameterSource.class),
                any(RowMapper.class));
    }
}
