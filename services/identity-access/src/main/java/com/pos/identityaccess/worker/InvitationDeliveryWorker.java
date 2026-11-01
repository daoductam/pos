package com.pos.identityaccess.worker;

import com.pos.identityaccess.application.IdGenerator;
import com.pos.identityaccess.application.model.CognitoActivation;
import com.pos.identityaccess.application.model.CognitoInvitationRequest;
import com.pos.identityaccess.application.model.CognitoInvitationResult;
import com.pos.identityaccess.application.model.CognitoSubjectResolution;
import com.pos.identityaccess.application.model.InvitationDelivery;
import com.pos.identityaccess.application.model.OutboxEvent;
import com.pos.identityaccess.application.port.CognitoInvitationGateway;
import com.pos.identityaccess.application.port.InvitationDeliveryStore;
import com.pos.identityaccess.application.port.MembershipRepository;
import com.pos.identityaccess.application.port.OutboxStore;
import com.pos.identityaccess.application.port.UserRepository;
import com.pos.identityaccess.domain.model.Membership;
import com.pos.identityaccess.domain.model.MembershipStatus;
import com.pos.identityaccess.domain.model.User;
import com.pos.identityaccess.infrastructure.persistence.TransactionRunner;
import com.pos.identityaccess.observability.MetricsConfiguration.SecurityMetrics;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Component
public final class InvitationDeliveryWorker {

    private static final String SYSTEM_ACTOR = "identity-access-worker";

    private final InvitationDeliveryStore deliveries;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final OutboxStore outbox;
    private final CognitoInvitationGateway cognito;
    private final TransactionRunner transactions;
    private final SecurityMetrics metrics;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    public InvitationDeliveryWorker(
            InvitationDeliveryStore deliveries,
            MembershipRepository memberships,
            UserRepository users,
            OutboxStore outbox,
            CognitoInvitationGateway cognito,
            TransactionRunner transactions,
            SecurityMetrics metrics) {
        this(deliveries, memberships, users, outbox, cognito, transactions, metrics,
                new RetryPolicy(5, java.time.Duration.ofSeconds(30), java.time.Duration.ofMinutes(15)),
                Clock.systemUTC());
    }

    public InvitationDeliveryWorker(
            InvitationDeliveryStore deliveries,
            MembershipRepository memberships,
            UserRepository users,
            OutboxStore outbox,
            CognitoInvitationGateway cognito,
            TransactionRunner transactions,
            SecurityMetrics metrics,
            RetryPolicy retryPolicy,
            Clock clock) {
        this.deliveries = Objects.requireNonNull(deliveries, "deliveries");
        this.memberships = Objects.requireNonNull(memberships, "memberships");
        this.users = Objects.requireNonNull(users, "users");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.cognito = Objects.requireNonNull(cognito, "cognito");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public int processBatch(int limit) {
        return processBatch(Instant.now(clock), limit);
    }

    @Scheduled(
            fixedDelayString = "${app.worker.invitation-fixed-delay-ms:5000}",
            initialDelayString = "${app.worker.initial-delay-ms:1000}")
    public void scheduledRun() {
        processBatch(100);
    }

    public int processBatch(Instant now, int limit) {
        Objects.requireNonNull(now, "now");
        int processed = deliveries.findPending(now, limit).stream()
                .mapToInt(delivery -> process(delivery, now))
                .sum();
        metrics.observeDlqDepth(deliveries.countFailed());
        return processed;
    }

    public CognitoSubjectResolution reconcileActivation(CognitoActivation activation) {
        Objects.requireNonNull(activation, "activation");
        CognitoSubjectResolution resolution = cognito.resolveSubject(activation);
        if (resolution.status() != CognitoSubjectResolution.Status.MATCHED) {
            return resolution;
        }
        User user = users.findByNormalizedEmail(activation.normalizedEmail()).orElse(null);
        if (user == null) {
            return new CognitoSubjectResolution(CognitoSubjectResolution.Status.PENDING,
                    activation.subject());
        }
        if (user.cognitoSubject() != null && !user.cognitoSubject().equals(activation.subject())) {
            return new CognitoSubjectResolution(CognitoSubjectResolution.Status.DRIFTED,
                    user.cognitoSubject());
        }
        transactions.inTransaction(() -> {
            if (users.bindCognitoSubject(user.id(), activation.subject(), "MATCHED")) {
                activateInvitedMemberships(user.id(), activation.subject());
            }
        });
        return resolution;
    }

    private int process(InvitationDelivery delivery, Instant now) {
        Membership membership = memberships.findById(delivery.membershipId()).orElse(null);
        User user = membership == null ? null : users.findById(membership.userId()).orElse(null);
        if (membership == null || user == null || membership.status() != MembershipStatus.INVITED) {
            deliveries.markFailed(delivery.id(), now, "DELIVERY_TARGET_INVALID");
            metrics.recordInvitationFailure();
            metrics.recordDlq();
            return 1;
        }
        CognitoInvitationResult result;
        try {
            result = cognito.sendInvitation(new CognitoInvitationRequest(
                    delivery.id(), user.normalizedEmail(), user.displayName()));
        } catch (RuntimeException exception) {
            result = new CognitoInvitationResult(
                    CognitoInvitationResult.Status.RETRYABLE_FAILURE, "PROVIDER_UNAVAILABLE");
        }
        switch (result.status()) {
            case SENT -> deliveries.markSent(delivery.id(), now, "cognito:" + delivery.id());
            case PERMANENT_FAILURE -> terminal(delivery, now, safeCode(result.safeErrorCode()));
            case RETRYABLE_FAILURE -> retryOrTerminal(delivery, now, safeCode(result.safeErrorCode()));
        }
        if (result.status() != CognitoInvitationResult.Status.SENT) {
            metrics.recordInvitationFailure();
            metrics.recordCognitoError();
        }
        return 1;
    }

    private void retryOrTerminal(InvitationDelivery delivery, Instant now, String errorCode) {
        int nextAttempt = delivery.attemptCount() + 1;
        if (retryPolicy.exhausted(nextAttempt)) {
            terminal(delivery, now, errorCode);
            return;
        }
        deliveries.markRetry(delivery.id(), retryPolicy.nextAttemptAt(now, nextAttempt), errorCode);
    }

    private void terminal(InvitationDelivery delivery, Instant now, String errorCode) {
        deliveries.markFailed(delivery.id(), now, errorCode);
        metrics.recordDlq();
    }

    private void activateInvitedMemberships(java.util.UUID userId, String subject) {
        List<Membership> invited = memberships.findCurrentByUser(userId);
        for (Membership membership : invited) {
            Membership active = new Membership(membership.id(), membership.tenantId(), membership.userId(),
                    membership.branchId(), membership.role(), MembershipStatus.ACTIVE, membership.version(),
                    membership.createdBy(), SYSTEM_ACTOR, "cognito_subject_matched");
            if (memberships.updateIfVersionMatches(active, membership.version())) {
                String payload = "{\"membershipId\":\"" + membership.id()
                        + "\",\"tenantId\":\"" + membership.tenantId()
                        + "\",\"status\":\"ACTIVE\"}";
                outbox.save(new OutboxEvent(IdGenerator.uuidV7(), membership.tenantId(), membership.branchId(),
                        "MembershipActivated", 1, "Membership", membership.id(), payload,
                        "cognito-reconciliation:" + subject, "cognito-reconciliation", Instant.now(clock)));
            }
        }
    }

    private static String safeCode(String code) {
        return code == null || code.isBlank() || code.length() > 100 ? "PROVIDER_FAILURE" : code;
    }
}
