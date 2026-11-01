package com.pos.identityaccess.api;

import com.pos.identityaccess.api.dto.ApiEnvelope;
import com.pos.identityaccess.api.dto.BootstrapTenantRequest;
import com.pos.identityaccess.api.dto.TenantBootstrapResponse;
import com.pos.identityaccess.api.dto.TenantResponse;
import com.pos.identityaccess.application.ActorContextFactory;
import com.pos.identityaccess.application.BootstrapApplicationService;
import com.pos.identityaccess.application.RequestHasher;
import com.pos.identityaccess.application.TenantApplicationService;
import com.pos.identityaccess.application.model.IdempotencyCommand;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
public final class TenantController {

    private final TenantApplicationService tenants;
    private final ActorContextFactory actors;
    private final BootstrapApplicationService bootstrap;

    public TenantController(
            TenantApplicationService tenants,
            ActorContextFactory actors,
            BootstrapApplicationService bootstrap) {
        this.tenants = tenants;
        this.actors = actors;
        this.bootstrap = bootstrap;
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope<TenantBootstrapResponse>> bootstrap(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody BootstrapTenantRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        TenantBootstrapResponse response = bootstrap.bootstrap(body, authentication,
                new IdempotencyCommand(RequestContext.idempotencyKey(idempotencyKey),
                        RequestHasher.sha256(String.valueOf(body))));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiEnvelope<>(response, RequestContext.meta(request)));
    }


    @GetMapping("/{tenantId}")
    public ApiEnvelope<TenantResponse> get(
            @PathVariable UUID tenantId,
            Authentication authentication,
            HttpServletRequest request) {
        return new ApiEnvelope<>(tenants.get(tenantId, actors.resolve(authentication.getName(), tenantId)),
                RequestContext.meta(request));
    }
}
