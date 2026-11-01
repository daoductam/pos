package com.pos.identityaccess.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.List;

public final class JwtPrincipalResolver {

    public JwtPrincipal resolve(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new InvalidBearerTokenException("JWT subject is required");
        }
        if (jwt.getIssuer() == null || jwt.getAudience() == null || jwt.getAudience().isEmpty()
                || jwt.getExpiresAt() == null) {
            throw new InvalidBearerTokenException("JWT identity claims are incomplete");
        }
        return new JwtPrincipal(
                jwt.getSubject(),
                jwt.getIssuer().toString(),
                List.copyOf(jwt.getAudience()),
                jwt.getExpiresAt());
    }
}
