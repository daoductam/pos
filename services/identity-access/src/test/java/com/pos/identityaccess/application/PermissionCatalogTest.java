package com.pos.identityaccess.application;

import com.pos.identityaccess.domain.model.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionCatalogTest {

    @Test
    void shouldExposeOnlyRolePermissions() {
        assertThat(PermissionCatalog.forRole(Role.OWNER))
                .contains("employee:invite", "membership:revoke", "pos:quick-switch")
                .doesNotContain("kds:view");
        assertThat(PermissionCatalog.forRole(Role.MANAGER))
                .contains("employee:manage")
                .doesNotContain("tenant:read");
        assertThat(PermissionCatalog.forRole(Role.CASHIER))
                .contains("order:create", "payment:create")
                .doesNotContain("employee:manage");
        assertThat(PermissionCatalog.forRole(Role.KITCHEN))
                .containsExactlyInAnyOrder("branch:read", "kds:view");
    }

    @Test
    void shouldReturnEmptyPermissions_forUnknownRole() {
        assertThat(PermissionCatalog.forRole(null)).isEmpty();
    }
}
