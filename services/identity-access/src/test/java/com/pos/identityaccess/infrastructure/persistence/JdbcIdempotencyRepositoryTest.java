package com.pos.identityaccess.infrastructure.persistence;

import com.pos.identityaccess.application.model.IdempotencyKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class JdbcIdempotencyRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void shouldUseNullSafeTenantPredicate_whenLookingUpPlatformKey() {
        JdbcIdempotencyRepository repository = new JdbcIdempotencyRepository(jdbc);
        IdempotencyKey key = new IdempotencyKey(null, "platform-job", "bootstrap-1", "hash");
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(java.util.List.of());

        assertThat(repository.find(key)).isEmpty();
        verify(jdbc).query(
                org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("tenant_id = :tenantId")
                        && sql.contains("tenant_id IS NULL")),
                any(SqlParameterSource.class),
                any(org.springframework.jdbc.core.RowMapper.class));
    }
}
