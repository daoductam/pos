package com.pos.identityaccess.domain.policy;

import com.pos.identityaccess.domain.model.MembershipStatus;
import com.pos.identityaccess.domain.model.Role;

public final class OwnerInvariant {

    private OwnerInvariant() {
    }

    public static boolean canRemoveActiveOwner(Role targetRole,
                                               MembershipStatus targetStatus,
                                               long activeOwnerCount) {
        if (targetRole != Role.OWNER || targetStatus != MembershipStatus.ACTIVE) {
            return true;
        }
        return activeOwnerCount > 1;
    }
}
