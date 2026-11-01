package com.pos.identityaccess.application;

import com.pos.identityaccess.application.exception.ForbiddenException;
import com.pos.identityaccess.application.model.AuthorizationDecision;
import com.pos.identityaccess.application.model.AuthorizationRequest;
import com.pos.identityaccess.application.port.BranchRepository;
import com.pos.identityaccess.application.port.MembershipRepository;
import com.pos.identityaccess.application.port.UserRepository;
import com.pos.identityaccess.domain.model.Branch;
import com.pos.identityaccess.domain.model.BranchStatus;
import com.pos.identityaccess.domain.model.Membership;
import com.pos.identityaccess.domain.model.MembershipStatus;
import com.pos.identityaccess.domain.model.Role;
import com.pos.identityaccess.domain.model.User;
import com.pos.identityaccess.domain.model.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorizationDecisionServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final MembershipRepository memberships = mock(MembershipRepository.class);
    private final BranchRepository branches = mock(BranchRepository.class);
    private final AuthorizationDecisionService service =
            new AuthorizationDecisionService(users, memberships, branches);

    @Test
    void shouldAllowCurrentActiveMembershipForRequestedBranchPermission() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        Membership membership = membership(tenantId, branchId, Role.CASHIER, MembershipStatus.ACTIVE);
        when(users.findByCognitoSubject("sub")).thenReturn(OptionalFixtures.user());
        when(memberships.findActiveBySubjectAndTenant("sub", tenantId)).thenReturn(List.of(membership));
        when(branches.findByTenantAndId(tenantId, branchId)).thenReturn(OptionalFixtures.branch(tenantId, branchId));

        AuthorizationDecision decision = service.decide(new AuthorizationRequest(
                "sub", tenantId, branchId, "order:create", "order"));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.role()).isEqualTo(Role.CASHIER);
        assertThat(decision.membershipVersion()).isEqualTo(1L);
    }

    @Test
    void shouldDenyForeignBranchWithoutRevealingMembership() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        Membership membership = membership(tenantId, UUID.randomUUID(), Role.CASHIER, MembershipStatus.ACTIVE);
        when(users.findByCognitoSubject("sub")).thenReturn(OptionalFixtures.user());
        when(memberships.findActiveBySubjectAndTenant("sub", tenantId)).thenReturn(List.of(membership));

        AuthorizationDecision decision = service.decide(new AuthorizationRequest(
                "sub", tenantId, branchId, "order:create", null));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("INVALID_SCOPE");
    }

    @Test
    void shouldDenyArchivedBranch() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        Membership membership = membership(tenantId, branchId, Role.CASHIER, MembershipStatus.ACTIVE);
        when(users.findByCognitoSubject("sub")).thenReturn(OptionalFixtures.user());
        when(memberships.findActiveBySubjectAndTenant("sub", tenantId)).thenReturn(List.of(membership));
        when(branches.findByTenantAndId(tenantId, branchId)).thenReturn(OptionalFixtures.archivedBranch(tenantId, branchId));

        AuthorizationDecision decision = service.decide(new AuthorizationRequest(
                "sub", tenantId, branchId, "order:create", null));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("BRANCH_UNAVAILABLE");
    }

    private static Membership membership(
            UUID tenantId, UUID branchId, Role role, MembershipStatus status) {
        return new Membership(UUID.randomUUID(), tenantId, UUID.randomUUID(), branchId, role,
                status, 1L, "seed", "seed");
    }

    private static final class OptionalFixtures {
        private static java.util.Optional<User> user() {
            Instant now = Instant.now();
            return java.util.Optional.of(new User(UUID.randomUUID(), "sub", "cashier@example.com",
                    "Cashier", null, UserStatus.ACTIVE, "ALIGNED", now, now));
        }

        private static java.util.Optional<Branch> branch(UUID tenantId, UUID branchId) {
            return java.util.Optional.of(new Branch(branchId, tenantId, "D1", "d1", "District 1",
                    null, BranchStatus.ACTIVE, 1L, Instant.now(), Instant.now(), "seed", "seed"));
        }

        private static java.util.Optional<Branch> archivedBranch(UUID tenantId, UUID branchId) {
            return java.util.Optional.of(new Branch(branchId, tenantId, "D1", "d1", "District 1",
                    null, BranchStatus.ARCHIVED, 1L, Instant.now(), Instant.now(), "seed", "seed"));
        }
    }
}
