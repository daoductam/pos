package com.pos.identityaccess.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class MetricsConfiguration {

    @Bean
    SecurityMetrics securityMetrics(MeterRegistry registry) {
        AtomicLong outboxLagSeconds = new AtomicLong();
        AtomicLong dlqDepth = new AtomicLong();
        Gauge.builder("identity_outbox_lag_seconds", outboxLagSeconds, AtomicLong::get)
                .description("Age in seconds of the oldest pending outbox event")
                .register(registry);
        Gauge.builder("identity_dlq_depth", dlqDepth, AtomicLong::get)
                .description("Current worker terminal-failure depth")
                .register(registry);
        return new SecurityMetrics(
                Counter.builder("identity_authorization_denied_total")
                        .description("Authorization decisions denied by Identity Access")
                        .register(registry),
                Counter.builder("identity_invitation_failure_total")
                        .description("Invitation delivery failures")
                        .register(registry),
                Counter.builder("identity_outbox_retry_total")
                        .description("Transactional outbox retry attempts")
                        .register(registry),
                Counter.builder("identity_worker_dlq_total")
                        .description("Worker items moved to terminal failure state")
                        .register(registry),
                Counter.builder("identity_conflict_total")
                        .description("Identity mutations rejected due to a state conflict")
                        .register(registry),
                Counter.builder("identity_cognito_error_total")
                        .description("Cognito adapter errors")
                        .register(registry),
                outboxLagSeconds,
                dlqDepth);
    }

    public record SecurityMetrics(
            Counter authorizationDenied,
            Counter invitationFailures,
            Counter outboxRetries,
            Counter dlqItems,
            Counter conflicts,
            Counter cognitoErrors,
            AtomicLong outboxLagSeconds,
            AtomicLong dlqDepth) {

        public SecurityMetrics(
                Counter authorizationDenied,
                Counter invitationFailures,
                Counter outboxRetries,
                Counter dlqItems) {
            this(authorizationDenied, invitationFailures, outboxRetries, dlqItems,
                    null, null, null, null);
        }

        public static SecurityMetrics noop() {
            return new SecurityMetrics(null, null, null, null, null, null, null, null);
        }

        public void recordAuthorizationDenied() {
            if (authorizationDenied != null) {
                authorizationDenied.increment();
            }
        }

        public void recordInvitationFailure() {
            if (invitationFailures != null) {
                invitationFailures.increment();
            }
        }

        public void recordOutboxRetry() {
            if (outboxRetries != null) {
                outboxRetries.increment();
            }
        }

        public void recordDlq() {
            if (dlqItems != null) {
                dlqItems.increment();
            }
            if (dlqDepth != null) {
                dlqDepth.incrementAndGet();
            }
        }

        public void recordConflict() {
            if (conflicts != null) {
                conflicts.increment();
            }
        }

        public void recordCognitoError() {
            if (cognitoErrors != null) {
                cognitoErrors.increment();
            }
        }

        public void observeOutboxLag(Duration lag) {
            if (outboxLagSeconds != null && lag != null) {
                outboxLagSeconds.set(Math.max(0L, lag.toSeconds()));
            }
        }

        public void observeDlqDepth(long depth) {
            if (dlqDepth != null) {
                dlqDepth.set(Math.max(0L, depth));
            }
        }
    }
}
