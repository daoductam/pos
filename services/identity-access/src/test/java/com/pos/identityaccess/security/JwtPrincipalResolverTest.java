package com.pos.identityaccess.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.AuthenticationException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPrincipalResolverTest {

    private final JwtPrincipalResolver resolver = new JwtPrincipalResolver();

    @Test
    void shouldResolveIdentityClaims_whenJwtIsValid() {
        Jwt jwt = Jwt.withTokenValue("opaque-token")
                .header("alg", "RS256")
                .issuer("https://cognito.example")
                .audience(List.of("identity-access"))
                .subject("user-sub")
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T01:00:00Z"))
                .claim("roles", List.of("OWNER"))
                .build();

        JwtPrincipal principal = resolver.resolve(jwt);

        assertThat(principal.subject()).isEqualTo("user-sub");
        assertThat(principal.issuer()).isEqualTo("https://cognito.example");
        assertThat(principal.audience()).containsExactly("identity-access");
        assertThat(principal.expiresAt()).isEqualTo(Instant.parse("2026-01-01T01:00:00Z"));
    }

    @Test
    void shouldRejectJwtWithoutSubject() {
        Jwt jwt = Jwt.withTokenValue("opaque-token")
                .header("alg", "RS256")
                .issuer("https://cognito.example")
                .audience(List.of("identity-access"))
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T01:00:00Z"))
                .build();

        assertThatThrownBy(() -> resolver.resolve(jwt))
                .isInstanceOf(AuthenticationException.class);
    }
}
