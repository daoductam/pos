package com.pos.identityaccess.worker;

import com.pos.identityaccess.application.model.OutboxEvent;
import com.pos.identityaccess.application.model.PublishResult;
import com.pos.identityaccess.application.port.OutboxPublisher;
import com.pos.identityaccess.application.port.OutboxStore;
import com.pos.identityaccess.observability.MetricsConfiguration.SecurityMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherWorkerTest {

    private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");

    @Mock
    private OutboxStore outbox;
    @Mock
    private OutboxPublisher publisher;

    @Test
    void shouldMoveOutboxToTerminalState_afterBoundedFailures() {
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), null,
                "MembershipChanged", 1, "Membership", UUID.randomUUID(),
                "{\"status\":\"ACTIVE\"}", "corr-1", "cause-1", NOW, 4);
        when(outbox.findPending(NOW, 10)).thenReturn(List.of(event));
        when(publisher.publish(event)).thenReturn(new PublishResult(
                PublishResult.Status.RETRYABLE_FAILURE, "BROKER_TIMEOUT"));

        worker().processBatch(NOW, 10);

        verify(publisher).publish(event);
        verify(outbox).markFailed(event.id(), NOW, "BROKER_TIMEOUT");
    }

    private OutboxPublisherWorker worker() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SecurityMetrics metrics = new SecurityMetrics(
                registry.counter("authorization-denied"),
                registry.counter("invitation-failure"),
                registry.counter("outbox-retry"),
                registry.counter("worker-dlq"));
        return new OutboxPublisherWorker(outbox, publisher, metrics,
                new RetryPolicy(5, Duration.ofSeconds(30), Duration.ofMinutes(15)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
