package com.pos.identityaccess.domain;

import com.pos.identityaccess.domain.model.MembershipStatus;
import com.pos.identityaccess.domain.policy.MembershipLifecycle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MembershipLifecycleTest {

    @Test
    void shouldAllowActivation_whenInvitationIsPending() {
        assertThat(MembershipLifecycle.transition(MembershipStatus.INVITED, MembershipStatus.ACTIVE))
                .isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    void shouldAllowSuspensionAndReactivation_whenMembershipIsActive() {
        assertThat(MembershipLifecycle.transition(MembershipStatus.ACTIVE, MembershipStatus.SUSPENDED))
                .isEqualTo(MembershipStatus.SUSPENDED);
        assertThat(MembershipLifecycle.transition(MembershipStatus.SUSPENDED, MembershipStatus.ACTIVE))
                .isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    void shouldRejectTransition_whenMembershipIsRevoked() {
        assertThatThrownBy(() -> MembershipLifecycle.transition(
                MembershipStatus.REVOKED, MembershipStatus.ACTIVE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldAllowRevocation_fromEveryNonTerminalState() {
        assertThat(MembershipLifecycle.transition(MembershipStatus.INVITED, MembershipStatus.REVOKED))
                .isEqualTo(MembershipStatus.REVOKED);
        assertThat(MembershipLifecycle.transition(MembershipStatus.ACTIVE, MembershipStatus.REVOKED))
                .isEqualTo(MembershipStatus.REVOKED);
        assertThat(MembershipLifecycle.transition(MembershipStatus.SUSPENDED, MembershipStatus.REVOKED))
                .isEqualTo(MembershipStatus.REVOKED);
    }

    @Test
    void shouldRejectInvalidLifecycleTransitions() {
        assertThatThrownBy(() -> MembershipLifecycle.transition(
                MembershipStatus.INVITED, MembershipStatus.SUSPENDED))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MembershipLifecycle.transition(
                MembershipStatus.ACTIVE, MembershipStatus.INVITED))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MembershipLifecycle.transition(
                MembershipStatus.SUSPENDED, MembershipStatus.INVITED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldReturnSameState_whenTransitionIsIdempotent() {
        for (MembershipStatus status : MembershipStatus.values()) {
            assertThat(MembershipLifecycle.transition(status, status)).isEqualTo(status);
        }
    }

    @Test
    void shouldRejectNullLifecycleState() {
        assertThatThrownBy(() -> MembershipLifecycle.transition(null, MembershipStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MembershipLifecycle.transition(MembershipStatus.ACTIVE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
