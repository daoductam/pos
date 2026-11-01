package com.pos.identityaccess.application.model;

import com.pos.identityaccess.domain.model.Role;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ActorContext(
        String subject,
        UUID tenantId,
        Role role,
        Set<UUID> branchIds) {

    public ActorContext {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(role, "role");
        branchIds = Collections.unmodifiableSet(new LinkedHashSet<>(
                Objects.requireNonNull(branchIds, "branchIds")));
        if (role.requiresBranch() && branchIds.isEmpty()) {
            throw new IllegalArgumentException("branch-scoped role requires at least one branch");
        }
    }

    public static ActorContext owner(String subject, UUID tenantId) {
        return new ActorContext(subject, tenantId, Role.OWNER, Set.of());
    }

    public static ActorContext manager(String subject, UUID tenantId, UUID branchId) {
        return new ActorContext(subject, tenantId, Role.MANAGER, Set.of(branchId));
    }

    public boolean hasBranch(UUID branchId) {
        return role == Role.OWNER || branchIds.contains(branchId);
    }
}
