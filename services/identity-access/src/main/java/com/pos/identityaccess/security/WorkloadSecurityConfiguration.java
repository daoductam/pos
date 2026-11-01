package com.pos.identityaccess.security;

import com.pos.identityaccess.api.ApiSecurityErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class WorkloadSecurityConfiguration {

    public static final String WORKLOAD_AUTHORITY = "ROLE_WORKLOAD";

    @Bean
    @Order(1)
    SecurityFilterChain internalSecurityFilterChain(
            HttpSecurity http, ApiSecurityErrorHandler errorHandler) throws Exception {
        http
                .securityMatcher("/internal/v1/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                // The ingress/runtime supplies the authenticated workload principal after mTLS.
                // Without that principal, the internal route fails closed.
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().hasAuthority(WORKLOAD_AUTHORITY));
        return http.build();
    }
}
