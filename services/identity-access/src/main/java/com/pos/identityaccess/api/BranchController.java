package com.pos.identityaccess.api;

import com.pos.identityaccess.api.dto.ApiEnvelope;
import com.pos.identityaccess.api.dto.BranchResponse;
import com.pos.identityaccess.api.dto.CreateBranchRequest;
import com.pos.identityaccess.api.dto.UpdateBranchRequest;
import com.pos.identityaccess.application.ActorContextFactory;
import com.pos.identityaccess.application.BranchApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/branches")
public final class BranchController {

    private final BranchApplicationService branches;
    private final ActorContextFactory actors;

    public BranchController(BranchApplicationService branches, ActorContextFactory actors) {
        this.branches = branches;
        this.actors = actors;
    }

    @GetMapping
    public ApiEnvelope<List<BranchResponse>> list(
            @PathVariable UUID tenantId,
            Authentication authentication,
            HttpServletRequest request) {
        return new ApiEnvelope<>(branches.list(tenantId, actors.resolve(authentication.getName(), tenantId)),
                RequestContext.meta(request));
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope<BranchResponse>> create(
            @PathVariable UUID tenantId,
            @RequestBody CreateBranchRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        BranchResponse response = branches.create(tenantId, body,
                actors.resolve(authentication.getName(), tenantId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiEnvelope<>(response, RequestContext.meta(request)));
    }

    @PatchMapping("/{branchId}")
    public ApiEnvelope<BranchResponse> update(
            @PathVariable UUID tenantId,
            @PathVariable UUID branchId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody UpdateBranchRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        BranchResponse response = branches.update(tenantId, branchId, body,
                RequestContext.version(ifMatch), actors.resolve(authentication.getName(), tenantId));
        return new ApiEnvelope<>(response, RequestContext.meta(request));
    }

    @PostMapping("/{branchId}/archive")
    public ApiEnvelope<BranchResponse> archive(
            @PathVariable UUID tenantId,
            @PathVariable UUID branchId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody com.pos.identityaccess.api.dto.ReasonRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        BranchResponse response = branches.archive(tenantId, branchId, body.reason(),
                RequestContext.version(ifMatch), actors.resolve(authentication.getName(), tenantId));
        return new ApiEnvelope<>(response, RequestContext.meta(request));
    }
}
