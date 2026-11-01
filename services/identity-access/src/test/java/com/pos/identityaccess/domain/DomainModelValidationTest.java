package com.pos.identityaccess.domain;

import com.pos.identityaccess.domain.model.Membership;
import com.pos.identityaccess.domain.model.MembershipStatus;
import com.pos.identityaccess.domain.model.Role;
import com.pos.identityaccess.domain.model.Branch;
import com.pos.identityaccess.domain.model.BranchStatus;
import com.pos.identityaccess.domain.model.Tenant;
import com.pos.identityaccess.domain.model.TenantStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainModelValidationTest {

    @Test
    void shouldRejectOwnerWithBranch() {
        assertThatThrownBy(() -> new Membership(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Role.OWNER,
                MembershipStatus.INVITED,
                1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectOperationalRoleWithoutBranch() {
        assertThatThrownBy(() -> new Membership(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                Role.CASHIER,
                MembershipStatus.INVITED,
                1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonPositiveVersionsAndBlankAuditActors() {
        assertThatThrownBy(() -> new Tenant(UUID.randomUUID(), "Tenant", TenantStatus.ACTIVE, 0,
                Instant.now(), Instant.now(), "owner", "owner"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Branch(UUID.randomUUID(), UUID.randomUUID(), "D1", "d1",
                "Branch", null, BranchStatus.ACTIVE, 1, Instant.now(), Instant.now(), "", "owner"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectOversizedMembershipReason() {
        assertThatThrownBy(() -> new Membership(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Role.CASHIER, MembershipStatus.SUSPENDED, 1,
                "owner", "manager", "r".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
