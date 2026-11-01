package com.pos.identityaccess.security;

import java.time.Instant;
import java.util.List;

public record JwtPrincipal(
        String subject,
        String issuer,
        List<String> audience,
        Instant expiresAt) {
}
