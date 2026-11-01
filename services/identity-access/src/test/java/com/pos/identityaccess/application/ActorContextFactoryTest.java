package com.pos.identityaccess.application;

import com.pos.identityaccess.application.model.ActorContext;
import com.pos.identityaccess.application.port.MembershipRepository;
import com.pos.identityaccess.domain.model.Membership;
import com.pos.identityaccess.domain.model.MembershipStatus;
import com.pos.identityaccess.domain.model.Role;
import com.pos.identityaccess.application.exception.ForbiddenException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActorContextFactoryTest {

    private final MembershipRepository memberships = mock(MembershipRepository.class);
    private final ActorContextFactory factory = new ActorContextFactory(memberships);

    @Test
    void shouldResolveOnlyActiveAssignedBranches() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        when(memberships.findActiveBySubjectAndTenant("sub", tenantId)).thenReturn(List.of(
                membership(tenantId, branchId, Role.MANAGER)));

        ActorContext context = factory.resolve("sub", tenantId);

        assertThat(context.role()).isEqualTo(Role.MANAGER);
        assertThat(context.branchIds()).containsExactly(branchId);
    }

    @Test
    void shouldPreferOwnerScopeWhenSubjectHasOwnerMembership() {
        UUID tenantId = UUID.randomUUID();
        when(memberships.findActiveBySubjectAndTenant("sub", tenantId)).thenReturn(List.of(
                membership(tenantId, null, Role.OWNER),
                membership(tenantId, UUID.randomUUID(), Role.MANAGER)));

        assertThat(factory.resolve("sub", tenantId).role()).isEqualTo(Role.OWNER);
        assertThat(factory.resolve("sub", tenantId).branchIds()).isEmpty();
    }

    @Test
    void shouldFailClosedWhenNoActiveMembershipExists() {
        UUID tenantId = UUID.randomUUID();
        when(memberships.findActiveBySubjectAndTenant("sub", tenantId)).thenReturn(List.of());

        assertThatThrownBy(() -> factory.resolve("sub", tenantId))
                .isInstanceOf(ForbiddenException.class);
    }

    private static Membership membership(UUID tenantId, UUID branchId, Role role) {
        return new Membership(UUID.randomUUID(), tenantId, UUID.randomUUID(), branchId, role,
                MembershipStatus.ACTIVE, 1L, "seed", "seed");
    }
}
