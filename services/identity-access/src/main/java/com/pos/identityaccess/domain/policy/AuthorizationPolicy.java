package com.pos.identityaccess.domain.policy;

import com.pos.identityaccess.application.model.ActorContext;
import com.pos.identityaccess.domain.model.Role;

import java.util.UUID;

public final class AuthorizationPolicy {

    private AuthorizationPolicy() {
    }

    public static boolean canManageMembership(ActorContext actor, Role targetRole, UUID targetBranchId) {
        if (actor == null || targetRole == null) {
            return false;
        }
        if (actor.role() == Role.OWNER) {
            return targetRole == Role.OWNER ? targetBranchId == null : targetBranchId != null;
        }
        return actor.role() == Role.MANAGER
                && (targetRole == Role.CASHIER || targetRole == Role.KITCHEN)
                && targetBranchId != null
                && actor.hasBranch(targetBranchId);
    }

    public static boolean canManageBranch(ActorContext actor, UUID branchId) {
        return actor != null && actor.role() == Role.OWNER && branchId != null;
    }

    public static boolean canReadBranch(ActorContext actor, UUID branchId) {
        return actor != null && branchId != null && actor.hasBranch(branchId);
    }
}
