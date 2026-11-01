package com.pos.identityaccess.api;

import com.pos.identityaccess.api.dto.ApiEnvelope;
import com.pos.identityaccess.api.dto.AuthorizationDecisionRequest;
import com.pos.identityaccess.api.dto.MeResponse;
import com.pos.identityaccess.application.AuthorizationDecisionService;
import com.pos.identityaccess.application.IdentityContextService;
import com.pos.identityaccess.application.model.AuthorizationRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class IdentityContextController {

    private final IdentityContextService identityContext;
    private final AuthorizationDecisionService authorization;

    public IdentityContextController(
            IdentityContextService identityContext,
            AuthorizationDecisionService authorization) {
        this.identityContext = identityContext;
        this.authorization = authorization;
    }

    @GetMapping("/api/v1/me")
    public ApiEnvelope<MeResponse> me(Authentication authentication, HttpServletRequest request) {
        return new ApiEnvelope<>(identityContext.me(authentication.getName()), RequestContext.meta(request));
    }

    @PostMapping("/internal/v1/authorization-decisions")
    public ResponseEntity<ApiEnvelope<com.pos.identityaccess.application.model.AuthorizationDecision>> decide(
            @RequestBody AuthorizationDecisionRequest body,
            HttpServletRequest request) {
        com.pos.identityaccess.application.model.AuthorizationDecision decision = authorization.decide(
                new AuthorizationRequest(body.subject(), body.tenantId(), body.branchId(),
                        body.requiredPermission(), body.requestedResource()));
        return ResponseEntity.ok(new ApiEnvelope<>(decision, RequestContext.meta(request)));
    }
}
