package com.pos.identityaccess.api;

import com.pos.identityaccess.api.dto.ApiEnvelope;
import com.pos.identityaccess.api.dto.BootstrapTenantRequest;
import com.pos.identityaccess.api.dto.TenantBootstrapResponse;
import com.pos.identityaccess.application.BootstrapApplicationService;
import com.pos.identityaccess.application.RequestHasher;
import com.pos.identityaccess.application.model.IdempotencyCommand;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class PlatformBootstrapController {

    private final BootstrapApplicationService bootstrap;

    public PlatformBootstrapController(BootstrapApplicationService bootstrap) {
        this.bootstrap = bootstrap;
    }

    @PostMapping("/internal/v1/tenants")
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
}
