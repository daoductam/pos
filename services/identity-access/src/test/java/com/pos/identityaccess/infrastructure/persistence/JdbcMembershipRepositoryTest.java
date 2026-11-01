package com.pos.identityaccess.infrastructure.persistence;

import com.pos.identityaccess.domain.model.Membership;
import com.pos.identityaccess.domain.model.MembershipStatus;
import com.pos.identityaccess.domain.model.Role;
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
class JdbcMembershipRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void shouldReturnFalseAndKeepScopePredicate_whenVersionIsStale() {
        JdbcMembershipRepository repository = new JdbcMembershipRepository(jdbc);
        Membership membership = new Membership(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Role.CASHIER,
                MembershipStatus.ACTIVE,
                2L);
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);

        boolean updated = repository.updateIfVersionMatches(membership, 1L);

        assertThat(updated).isFalse();
        verify(jdbc).update(
                org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("tenant_id = :tenantId")
                        && sql.contains("version = :expectedVersion")),
                any(SqlParameterSource.class));
    }
}
