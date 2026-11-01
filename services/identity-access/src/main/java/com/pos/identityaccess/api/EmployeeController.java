package com.pos.identityaccess.api;

import com.pos.identityaccess.api.dto.ApiEnvelope;
import com.pos.identityaccess.api.dto.EmployeeResponse;
import com.pos.identityaccess.application.ActorContextFactory;
import com.pos.identityaccess.application.MembershipApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/employees")
public final class EmployeeController {

    private final MembershipApplicationService memberships;
    private final ActorContextFactory actors;

    public EmployeeController(MembershipApplicationService memberships, ActorContextFactory actors) {
        this.memberships = memberships;
        this.actors = actors;
    }

    @GetMapping
    public ApiEnvelope<List<EmployeeResponse>> list(
            @PathVariable UUID tenantId,
            Authentication authentication,
            HttpServletRequest request) {
        return new ApiEnvelope<>(memberships.listEmployees(tenantId,
                actors.resolve(authentication.getName(), tenantId)), RequestContext.meta(request));
    }

    @GetMapping("/{userId}")
    public ApiEnvelope<EmployeeResponse> get(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId,
            Authentication authentication,
            HttpServletRequest request) {
        return new ApiEnvelope<>(memberships.getEmployee(tenantId, userId,
                actors.resolve(authentication.getName(), tenantId)), RequestContext.meta(request));
    }
}
