package com.pos.identityaccess.application;

import com.pos.identityaccess.domain.model.Role;

import java.util.LinkedHashSet;
import java.util.Set;

public final class PermissionCatalog {

    private PermissionCatalog() {
    }

    public static Set<String> forRole(Role role) {
        if (role == null) {
            return Set.of();
        }
        Set<String> permissions = new LinkedHashSet<>();
        switch (role) {
            case OWNER -> permissions.addAll(Set.of(
                    "tenant:read", "branch:read", "branch:manage", "employee:read",
                    "employee:invite", "employee:manage", "membership:assign",
                    "membership:suspend", "membership:revoke", "pos-device:manage",
                    "pos:quick-switch"));
            case MANAGER -> permissions.addAll(Set.of(
                    "branch:read", "employee:read", "employee:invite", "employee:manage",
                    "membership:assign", "membership:suspend", "membership:revoke",
                    "pos-device:manage", "pos:quick-switch"));
            case CASHIER -> permissions.addAll(Set.of(
                    "branch:read", "pos:quick-switch", "order:read", "order:create",
                    "payment:create"));
            case KITCHEN -> permissions.addAll(Set.of("branch:read", "kds:view"));
        }
        return Set.copyOf(permissions);
    }
}
