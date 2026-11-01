package com.pos.identityaccess.domain;

import com.pos.identityaccess.domain.model.MembershipStatus;
import com.pos.identityaccess.domain.model.Role;
import com.pos.identityaccess.domain.policy.OwnerInvariant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerInvariantTest {

    @Test
    void shouldPreventRevokingLastActiveOwner() {
        assertThat(OwnerInvariant.canRemoveActiveOwner(Role.OWNER, MembershipStatus.ACTIVE, 1))
                .isFalse();
    }

    @Test
    void shouldAllowRevokingOwnerWhenAnotherActiveOwnerExists() {
        assertThat(OwnerInvariant.canRemoveActiveOwner(Role.OWNER, MembershipStatus.ACTIVE, 2))
                .isTrue();
        assertThat(OwnerInvariant.canRemoveActiveOwner(Role.MANAGER, MembershipStatus.ACTIVE, 1))
                .isTrue();
    }

    @Test
    void shouldAllowNonOwnerOrNonActiveTarget_withoutOwnerCountRequirement() {
        assertThat(OwnerInvariant.canRemoveActiveOwner(Role.MANAGER, MembershipStatus.ACTIVE, 0))
                .isTrue();
        assertThat(OwnerInvariant.canRemoveActiveOwner(Role.OWNER, MembershipStatus.INVITED, 1))
                .isTrue();
        assertThat(OwnerInvariant.canRemoveActiveOwner(Role.OWNER, MembershipStatus.SUSPENDED, 1))
                .isTrue();
    }
}
