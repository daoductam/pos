package com.pos.identityaccess.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class JdbcInvitationDeliveryRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void shouldClaimQueuedDeliveryWithLease_whenWorkerPolls() {
        JdbcInvitationDeliveryRepository repository = new JdbcInvitationDeliveryRepository(jdbc);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        assertThat(repository.findPending(Instant.parse("2026-10-13T00:00:00Z"), 10)).isEmpty();
        verify(jdbc).query(
                org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("FOR UPDATE SKIP LOCKED")
                        && sql.contains("status = 'QUEUED'")
                        && sql.contains("SET next_attempt_at = :leaseUntil")),
                any(SqlParameterSource.class),
                any(RowMapper.class));
    }
}
