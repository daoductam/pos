package com.pos.identityaccess.observability;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedactionPolicyTest {

    @Test
    void shouldRemoveSecretsAndMaskPersonalData() {
        Map<String, Object> redacted = RedactionPolicy.redact(Map.of(
                "authorization", "Bearer token",
                "password", "secret",
                "pin", "482915",
                "email", "owner@example.com",
                "phone", "+84901234567",
                "safe", "value"));

        assertThat(redacted).containsEntry("safe", "value");
        assertThat(redacted).doesNotContainKeys("authorization", "password", "pin");
        assertThat(redacted.get("email")).isEqualTo("o***@example.com");
        assertThat(redacted.get("phone")).isEqualTo("*********567");
    }
}
