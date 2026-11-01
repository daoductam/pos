package com.pos.identityaccess.observability;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityEventFactoryTest {

    @Test
    void shouldIncludeEffectivePrincipalAndRedactNestedSensitiveMetadata() {
        SecurityEventFactory.SecurityEvent event = new SecurityEventFactory().create(
                "MembershipRevoked", "DENIED", "actor-sub", "effective-sub",
                UUID.randomUUID(), UUID.randomUUID(), "membership-1", "corr-1",
                Map.of("emailAddress", "owner@example.com", "phoneNumber", "+84901234567",
                        "nested", Map.of("access_token", "secret", "safe", "value")));

        assertThat(event.actorSubject()).isEqualTo("actor-sub");
        assertThat(event.effectivePrincipal()).isEqualTo("effective-sub");
        assertThat(event.metadata()).containsEntry("emailAddress", "o***@example.com");
        assertThat(event.metadata()).containsEntry("phoneNumber", "*********567");
        assertThat(event.metadata().get("nested")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .doesNotContainKey("access_token")
                .containsEntry("safe", "value");
    }
}
