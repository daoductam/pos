package com.pos.identityaccess.worker;

import com.pos.identityaccess.application.model.CognitoInvitationResult;
import com.pos.identityaccess.application.model.CognitoActivation;
import com.pos.identityaccess.application.model.InvitationDelivery;
import com.pos.identityaccess.application.model.OutboxEvent;
import com.pos.identityaccess.application.port.CognitoInvitationGateway;
import com.pos.identityaccess.application.port.InvitationDeliveryStore;
import com.pos.identityaccess.application.port.MembershipRepository;
import com.pos.identityaccess.application.port.OutboxStore;
import com.pos.identityaccess.application.port.UserRepository;
import com.pos.identityaccess.infrastructure.cognito.LocalCognitoInvitationGateway;
import com.pos.identityaccess.domain.model.Membership;
import com.pos.identityaccess.domain.model.MembershipStatus;
import com.pos.identityaccess.domain.model.Role;
import com.pos.identityaccess.domain.model.User;
import com.pos.identityaccess.domain.model.UserStatus;
import com.pos.identityaccess.infrastructure.persistence.TransactionRunner;
import com.pos.identityaccess.observability.MetricsConfiguration.SecurityMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class InvitationDeliveryWorkerTest {

    private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");

    @Mock
    private InvitationDeliveryStore deliveries;
    @Mock
    private MembershipRepository memberships;
    @Mock
    private UserRepository users;
    @Mock
    private OutboxStore outbox;
    @Mock
    private CognitoInvitationGateway cognito;
    @Mock
    private TransactionRunner transactions;

    private SecurityMetrics metrics;
    private Membership membership;
    private User user;

    @BeforeEach
    void setUp() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        metrics = new SecurityMetrics(
                registry.counter("authorization-denied"),
                registry.counter("invitation-failure"),
                registry.counter("outbox-retry"),
                registry.counter("worker-dlq"));
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        membership = new Membership(UUID.randomUUID(), tenantId, userId, UUID.randomUUID(),
                Role.CASHIER, MembershipStatus.INVITED, 1L);
        user = new User(userId, null, "cashier@example.com", "Cashier", null,
                UserStatus.ACTIVE, "PENDING", NOW, NOW);
        lenient().when(memberships.findById(membership.id())).thenReturn(Optional.of(membership));
        lenient().when(users.findById(user.id())).thenReturn(Optional.of(user));
    }

    @Test
    void shouldMarkDeliverySentAndNeverClaimItAgain_whenProviderAccepts() {
        InvitationDelivery delivery = InvitationDelivery.queued(UUID.randomUUID(), membership.id());
        when(deliveries.findPending(NOW, 10)).thenReturn(List.of(delivery));
        when(cognito.sendInvitation(any())).thenReturn(
                new CognitoInvitationResult(CognitoInvitationResult.Status.SENT, null));

        worker(new RetryPolicy(5, Duration.ofSeconds(30), Duration.ofMinutes(15)))
                .processBatch(NOW, 10);

        verify(cognito).sendInvitation(any());
        verify(deliveries).markSent(delivery.id(), NOW, "cognito:" + delivery.id());
        verify(deliveries, never()).markRetry(any(), any(), any());
    }

    @Test
    void shouldKeepDeliveryRetryable_whenProviderTimesOut() {
        InvitationDelivery delivery = InvitationDelivery.queued(UUID.randomUUID(), membership.id());
        when(deliveries.findPending(NOW, 10)).thenReturn(List.of(delivery));
        when(cognito.sendInvitation(any())).thenReturn(
                new CognitoInvitationResult(CognitoInvitationResult.Status.RETRYABLE_FAILURE,
                        "PROVIDER_TIMEOUT"));

        worker(new RetryPolicy(5, Duration.ofSeconds(30), Duration.ofMinutes(15)))
                .processBatch(NOW, 10);

        verify(deliveries).markRetry(delivery.id(), NOW.plusSeconds(30), "PROVIDER_TIMEOUT");
        verify(deliveries, never()).markFailed(any(), any(), any());
    }

    @Test
    void shouldMoveDeliveryToTerminalState_whenMaxAttemptsIsExceeded() {
        InvitationDelivery delivery = new InvitationDelivery(UUID.randomUUID(), membership.id(),
                com.pos.identityaccess.application.model.InvitationDeliveryStatus.QUEUED,
                4, NOW, "PROVIDER_TIMEOUT", null);
        when(deliveries.findPending(NOW, 10)).thenReturn(List.of(delivery));
        when(cognito.sendInvitation(any())).thenReturn(
                new CognitoInvitationResult(CognitoInvitationResult.Status.RETRYABLE_FAILURE,
                        "PROVIDER_TIMEOUT"));

        worker(new RetryPolicy(5, Duration.ofSeconds(30), Duration.ofMinutes(15)))
                .processBatch(NOW, 10);

        verify(deliveries).markFailed(delivery.id(), NOW, "PROVIDER_TIMEOUT");
        verify(outbox, never()).save(any(OutboxEvent.class));
    }

    @Test
    void shouldNotActivateMembership_whenInvitationDeliveryIsTerminal() {
        InvitationDelivery delivery = new InvitationDelivery(UUID.randomUUID(), membership.id(),
                com.pos.identityaccess.application.model.InvitationDeliveryStatus.QUEUED,
                4, NOW, "PROVIDER_TIMEOUT", null);
        when(deliveries.findPending(NOW, 10)).thenReturn(List.of(delivery));
        when(cognito.sendInvitation(any())).thenReturn(
                new CognitoInvitationResult(CognitoInvitationResult.Status.PERMANENT_FAILURE,
                        "LOCAL_INVALID_RECIPIENT"));

        worker(new RetryPolicy(5, Duration.ofSeconds(30), Duration.ofMinutes(15)))
                .processBatch(NOW, 10);

        verify(deliveries).markFailed(delivery.id(), NOW, "LOCAL_INVALID_RECIPIENT");
        verify(memberships, never()).updateIfVersionMatches(any(), any(Long.class));
    }

    @Test
    void shouldBindSubjectAndActivateOnlyMatchingMembership_whenLocalFixtureReconciles() {
        LocalCognitoInvitationGateway localGateway = new LocalCognitoInvitationGateway();
        localGateway.sendInvitation(new com.pos.identityaccess.application.model.CognitoInvitationRequest(
                UUID.randomUUID(), user.normalizedEmail(), user.displayName()));
        String subject = localGateway.subjectForEmail(user.normalizedEmail());
        when(users.findByNormalizedEmail(user.normalizedEmail())).thenReturn(Optional.of(user));
        when(memberships.findCurrentByUser(user.id())).thenReturn(List.of(membership));
        when(users.bindCognitoSubject(user.id(), subject, "MATCHED")).thenReturn(true);
        when(memberships.updateIfVersionMatches(any(), any(Long.class))).thenReturn(true);

        InvitationDeliveryWorker activationWorker = new InvitationDeliveryWorker(
                deliveries, memberships, users, outbox, localGateway, transactions, metrics,
                new RetryPolicy(5, Duration.ofSeconds(30), Duration.ofMinutes(15)),
                Clock.fixed(NOW, ZoneOffset.UTC));
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(transactions).inTransaction(any(Runnable.class));

        assertThat(activationWorker.reconcileActivation(
                new CognitoActivation(subject, user.normalizedEmail())).status())
                .isEqualTo(com.pos.identityaccess.application.model.CognitoSubjectResolution.Status.MATCHED);
        verify(users).bindCognitoSubject(user.id(), subject, "MATCHED");
        verify(memberships).updateIfVersionMatches(any(Membership.class), any(Long.class));
        verify(outbox).save(any(OutboxEvent.class));
    }

    private InvitationDeliveryWorker worker(RetryPolicy policy) {
        return new InvitationDeliveryWorker(deliveries, memberships, users, outbox, cognito,
                transactions, metrics, policy, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
