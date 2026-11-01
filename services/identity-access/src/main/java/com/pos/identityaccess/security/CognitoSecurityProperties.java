package com.pos.identityaccess.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cognito")
public record CognitoSecurityProperties(String issuerUri, String audience) {

    public boolean enabled() {
        return issuerUri != null && !issuerUri.isBlank();
    }
}
