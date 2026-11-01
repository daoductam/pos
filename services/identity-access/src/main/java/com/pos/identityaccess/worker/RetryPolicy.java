package com.pos.identityaccess.worker;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record RetryPolicy(int maxAttempts, Duration initialDelay, Duration maxDelay) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        Objects.requireNonNull(initialDelay, "initialDelay");
        Objects.requireNonNull(maxDelay, "maxDelay");
        if (initialDelay.isNegative() || initialDelay.isZero()
                || maxDelay.isNegative() || maxDelay.isZero()
                || initialDelay.compareTo(maxDelay) > 0) {
            throw new IllegalArgumentException("retry delays must be positive and ordered");
        }
    }

    public boolean exhausted(int attemptCountAfterFailure) {
        return attemptCountAfterFailure >= maxAttempts;
    }

    public Instant nextAttemptAt(Instant now, int attemptCountAfterFailure) {
        Objects.requireNonNull(now, "now");
        if (attemptCountAfterFailure < 1) {
            throw new IllegalArgumentException("attemptCountAfterFailure must be positive");
        }
        long multiplier = 1L << Math.min(attemptCountAfterFailure - 1, 30);
        Duration delay;
        try {
            delay = initialDelay.multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            delay = maxDelay;
        }
        if (delay.compareTo(maxDelay) > 0) {
            delay = maxDelay;
        }
        return now.plus(delay);
    }
}
