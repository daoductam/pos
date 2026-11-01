package com.pos.identityaccess.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

public final class CognitoJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtPrincipalResolver principalResolver = new JwtPrincipalResolver();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        JwtPrincipal principal = principalResolver.resolve(jwt);
        return new JwtAuthenticationToken(jwt, List.of(), principal.subject());
    }
}
