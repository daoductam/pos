package com.pos.identityaccess.application;

import com.pos.identityaccess.application.model.AuthorizationScope;
import com.pos.identityaccess.domain.model.Role;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationScopeTest {

    @Test
    void shouldDefensivelyCopyBranchScope_andAllowOnlyAssignedBranches() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        var branches = new java.util.HashSet<>(Set.of(branchId));
        AuthorizationScope scope = new AuthorizationScope("manager-sub", tenantId, Role.MANAGER, branches);
        branches.clear();

        assertThat(scope.branchIds()).containsExactly(branchId);
        assertThat(scope.allowsBranch(branchId)).isTrue();
        assertThat(scope.allowsBranch(UUID.randomUUID())).isFalse();
        assertThatThrownBy(() -> scope.branchIds().add(UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectBranchScopedRoleWithoutAssignedBranch() {
        assertThatThrownBy(() -> new AuthorizationScope(
                "manager-sub", UUID.randomUUID(), Role.MANAGER, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
