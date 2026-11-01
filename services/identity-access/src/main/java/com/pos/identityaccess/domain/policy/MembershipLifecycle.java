package com.pos.identityaccess.domain.policy;

import com.pos.identityaccess.domain.model.MembershipStatus;

public final class MembershipLifecycle {

    private MembershipLifecycle() {
    }

    public static MembershipStatus transition(MembershipStatus current, MembershipStatus next) {
        if (current == null || next == null) {
            throw new IllegalArgumentException("membership statuses are required");
        }
        if (current == next) {
            return current;
        }
        boolean allowed = switch (current) {
            case INVITED -> next == MembershipStatus.ACTIVE || next == MembershipStatus.REVOKED;
            case ACTIVE -> next == MembershipStatus.SUSPENDED || next == MembershipStatus.REVOKED;
            case SUSPENDED -> next == MembershipStatus.ACTIVE || next == MembershipStatus.REVOKED;
            case REVOKED -> false;
        };
        if (!allowed) {
            throw new IllegalStateException("invalid membership transition: " + current + " -> " + next);
        }
        return next;
    }
}
