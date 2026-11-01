package com.pos.identityaccess.security;

import com.pos.identityaccess.api.ApiSecurityErrorHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(CognitoSecurityProperties.class)
public class JwtSecurityConfiguration {

    @Bean
    @Order(2)
    SecurityFilterChain publicSecurityFilterChain(
            HttpSecurity http,
            CognitoSecurityProperties properties,
            ApiSecurityErrorHandler errorHandler) throws Exception {
        http
                .securityMatcher(request -> !request.getServletPath().startsWith("/internal/v1/"))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler));

        if (properties.enabled()) {
            http.authorizeHttpRequests(auth -> auth
                            .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                            .anyRequest().authenticated())
                    .oauth2ResourceServer(oauth2 -> oauth2
                            .jwt(jwt -> jwt
                                    .decoder(JwtDecoders.fromIssuerLocation(properties.issuerUri()))
                                    .jwtAuthenticationConverter(new CognitoJwtAuthenticationConverter())));
        } else {
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health/**").permitAll()
                    .anyRequest().denyAll());
        }
        return http.build();
    }
}
