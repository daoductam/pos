package com.pos.identityaccess.domain;

import com.pos.identityaccess.application.model.ActorContext;
import com.pos.identityaccess.domain.model.Role;
import com.pos.identityaccess.domain.policy.AuthorizationPolicy;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationPolicyTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID branchA = UUID.randomUUID();
    private final UUID branchB = UUID.randomUUID();

    @Test
    void shouldAllowManagerToManageCashierInAssignedBranch() {
        ActorContext manager = ActorContext.manager("manager-sub", tenantId, branchA);

        assertThat(AuthorizationPolicy.canManageMembership(manager, Role.CASHIER, branchA))
                .isTrue();
    }

    @Test
    void shouldDenyManagerToManageForeignBranchOrElevatedRole() {
        ActorContext manager = ActorContext.manager("manager-sub", tenantId, branchA);

        assertThat(AuthorizationPolicy.canManageMembership(manager, Role.CASHIER, branchB))
                .isFalse();
        assertThat(AuthorizationPolicy.canManageMembership(manager, Role.MANAGER, branchA))
                .isFalse();
    }

    @Test
    void shouldAllowOwnerToManageAnyMembershipInTenant() {
        ActorContext owner = ActorContext.owner("owner-sub", tenantId);

        assertThat(AuthorizationPolicy.canManageMembership(owner, Role.MANAGER, branchB))
                .isTrue();
        assertThat(AuthorizationPolicy.canManageBranch(owner, branchB)).isTrue();
    }

    @Test
    void shouldDenyNullOrInvalidManagementInputs() {
        ActorContext owner = ActorContext.owner("owner-sub", tenantId);

        assertThat(AuthorizationPolicy.canManageMembership(null, Role.CASHIER, branchA))
                .isFalse();
        assertThat(AuthorizationPolicy.canManageMembership(owner, null, branchA))
                .isFalse();
        assertThat(AuthorizationPolicy.canManageMembership(owner, Role.OWNER, branchA))
                .isFalse();
        assertThat(AuthorizationPolicy.canManageMembership(owner, Role.MANAGER, null))
                .isFalse();
        assertThat(AuthorizationPolicy.canManageBranch(owner, null)).isFalse();
        assertThat(AuthorizationPolicy.canReadBranch(owner, null)).isFalse();
    }

    @Test
    void shouldAllowManagerToReadOnlyAssignedBranch() {
        ActorContext manager = ActorContext.manager("manager-sub", tenantId, branchA);

        assertThat(AuthorizationPolicy.canReadBranch(manager, branchA)).isTrue();
        assertThat(AuthorizationPolicy.canReadBranch(manager, branchB)).isFalse();
    }
}
