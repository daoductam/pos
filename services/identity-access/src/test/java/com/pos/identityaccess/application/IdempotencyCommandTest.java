package com.pos.identityaccess.application;

import com.pos.identityaccess.application.model.IdempotencyCommand;
import com.pos.identityaccess.application.model.IdempotencyKey;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyCommandTest {

    @Test
    void shouldProduceStableHash_forSameCanonicalRequest() {
        String first = RequestHasher.sha256("role=CASHIER&branch=branch-a");
        String second = RequestHasher.sha256("role=CASHIER&branch=branch-a");

        assertThat(first).isEqualTo(second).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(first).isNotEqualTo(RequestHasher.sha256("role=KITCHEN&branch=branch-a"));
    }

    @Test
    void shouldValidateIdempotencyCommandBoundaries() {
        assertThatThrownBy(() -> new IdempotencyCommand("", "hash"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyCommand("key", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyCommand("k".repeat(201), "hash"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowNullTenantOnlyForPlatformScopedIdempotencyKey() {
        IdempotencyKey platformKey = new IdempotencyKey(null, "platform", "bootstrap-1", "hash");
        IdempotencyKey tenantKey = new IdempotencyKey(UUID.randomUUID(), "owner-sub", "invite-1", "hash");

        assertThat(platformKey.tenantId()).isNull();
        assertThat(tenantKey.tenantId()).isNotNull();
    }
}
