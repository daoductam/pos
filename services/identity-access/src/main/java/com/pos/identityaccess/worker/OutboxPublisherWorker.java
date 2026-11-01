package com.pos.identityaccess.worker;

import com.pos.identityaccess.application.model.OutboxEvent;
import com.pos.identityaccess.application.model.PublishResult;
import com.pos.identityaccess.application.port.OutboxPublisher;
import com.pos.identityaccess.application.port.OutboxStore;
import com.pos.identityaccess.observability.MetricsConfiguration.SecurityMetrics;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Component
public final class OutboxPublisherWorker {

    private final OutboxStore outbox;
    private final OutboxPublisher publisher;
    private final SecurityMetrics metrics;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    public OutboxPublisherWorker(
            OutboxStore outbox,
            OutboxPublisher publisher,
            SecurityMetrics metrics) {
        this(outbox, publisher, metrics,
                new RetryPolicy(5, java.time.Duration.ofSeconds(30), java.time.Duration.ofMinutes(15)),
                Clock.systemUTC());
    }

    public OutboxPublisherWorker(
            OutboxStore outbox,
            OutboxPublisher publisher,
            SecurityMetrics metrics,
            RetryPolicy retryPolicy,
            Clock clock) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public int processBatch(int limit) {
        return processBatch(Instant.now(clock), limit);
    }

    @Scheduled(
            fixedDelayString = "${app.worker.outbox-fixed-delay-ms:2000}",
            initialDelayString = "${app.worker.initial-delay-ms:1000}")
    public void scheduledRun() {
        processBatch(100);
    }

    public int processBatch(Instant now, int limit) {
        Objects.requireNonNull(now, "now");
        var claimed = outbox.findPending(now, limit);
        claimed.stream()
                .map(OutboxEvent::occurredAt)
                .filter(Objects::nonNull)
                .mapToLong(occurredAt -> Math.max(0L, Duration.between(occurredAt, now).toSeconds()))
                .max()
                .ifPresent(lag -> metrics.observeOutboxLag(Duration.ofSeconds(lag)));
        int processed = claimed.stream()
                .mapToInt(event -> process(event, now))
                .sum();
        metrics.observeDlqDepth(outbox.countFailed());
        return processed;
    }

    private int process(OutboxEvent event, Instant now) {
        PublishResult result;
        try {
            result = publisher.publish(event);
        } catch (RuntimeException exception) {
            result = new PublishResult(PublishResult.Status.RETRYABLE_FAILURE, "BROKER_UNAVAILABLE");
        }
        String errorCode = safeCode(result.safeErrorCode());
        switch (result.status()) {
            case PUBLISHED -> outbox.markPublished(event.id(), now);
            case PERMANENT_FAILURE -> terminal(event, now, errorCode);
            case RETRYABLE_FAILURE -> retryOrTerminal(event, now, errorCode);
        }
        if (result.status() != PublishResult.Status.PUBLISHED) {
            metrics.recordOutboxRetry();
        }
        return 1;
    }

    private void retryOrTerminal(OutboxEvent event, Instant now, String errorCode) {
        int nextAttempt = attemptCount(event) + 1;
        if (retryPolicy.exhausted(nextAttempt)) {
            terminal(event, now, errorCode);
            return;
        }
        outbox.markRetry(event.id(), retryPolicy.nextAttemptAt(now, nextAttempt), errorCode);
    }

    private void terminal(OutboxEvent event, Instant now, String errorCode) {
        outbox.markFailed(event.id(), now, errorCode);
        metrics.recordDlq();
    }

    /* The claimed event contract intentionally has no mutable attempt field.
       The worker uses the bounded retry schedule; the store owns the durable count. */
    private static int attemptCount(OutboxEvent event) {
        return event.attemptCount();
    }

    private static String safeCode(String code) {
        return code == null || code.isBlank() || code.length() > 100 ? "BROKER_FAILURE" : code;
    }
}
