package com.pos.identityaccess.worker;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    @Test
    void shouldBoundExponentialDelayAndMarkFifthAttemptTerminal() {
        RetryPolicy policy = new RetryPolicy(5, Duration.ofSeconds(30), Duration.ofMinutes(2));
        Instant now = Instant.parse("2026-10-13T00:00:00Z");

        assertThat(policy.nextAttemptAt(now, 1)).isEqualTo(now.plusSeconds(30));
        assertThat(policy.nextAttemptAt(now, 3)).isEqualTo(now.plusSeconds(120));
        assertThat(policy.nextAttemptAt(now, 5)).isEqualTo(now.plusSeconds(120));
        assertThat(policy.exhausted(5)).isTrue();
    }
}
