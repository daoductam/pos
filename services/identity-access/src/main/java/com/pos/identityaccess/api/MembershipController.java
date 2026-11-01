package com.pos.identityaccess.api;

import com.pos.identityaccess.api.dto.ApiEnvelope;
import com.pos.identityaccess.api.dto.InvitationDeliveryResponse;
import com.pos.identityaccess.api.dto.InviteMembershipRequest;
import com.pos.identityaccess.api.dto.MembershipResponse;
import com.pos.identityaccess.api.dto.ReasonRequest;
import com.pos.identityaccess.api.dto.UpdateMembershipRequest;
import com.pos.identityaccess.application.ActorContextFactory;
import com.pos.identityaccess.application.MembershipApplicationService;
import com.pos.identityaccess.application.RequestHasher;
import com.pos.identityaccess.application.model.IdempotencyCommand;
import com.pos.identityaccess.domain.model.MembershipStatus;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/memberships")
public final class MembershipController {

    private final MembershipApplicationService memberships;
    private final ActorContextFactory actors;

    public MembershipController(MembershipApplicationService memberships, ActorContextFactory actors) {
        this.memberships = memberships;
        this.actors = actors;
    }

    @GetMapping
    public ApiEnvelope<java.util.List<MembershipResponse>> list(
            @PathVariable UUID tenantId,
            Authentication authentication,
            HttpServletRequest request) {
        java.util.List<MembershipResponse> response = memberships.list(tenantId,
                actors.resolve(authentication.getName(), tenantId));
        return new ApiEnvelope<>(response, RequestContext.meta(request));
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope<MembershipResponse>> invite(
            @PathVariable UUID tenantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody InviteMembershipRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        IdempotencyCommand command = command(idempotencyKey, body);
        MembershipResponse response = memberships.invite(tenantId, body,
                actors.resolve(authentication.getName(), tenantId), command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiEnvelope<>(response, RequestContext.meta(request)));
    }

    @GetMapping("/{membershipId}")
    public ApiEnvelope<MembershipResponse> get(
            @PathVariable UUID tenantId,
            @PathVariable UUID membershipId,
            Authentication authentication,
            HttpServletRequest request) {
        MembershipResponse response = memberships.get(tenantId, membershipId,
                actors.resolve(authentication.getName(), tenantId));
        return new ApiEnvelope<>(response, RequestContext.meta(request));
    }

    @PatchMapping("/{membershipId}")
    public ApiEnvelope<MembershipResponse> update(
            @PathVariable UUID tenantId,
            @PathVariable UUID membershipId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody UpdateMembershipRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        long version = RequestContext.version(ifMatch);
        MembershipResponse response = memberships.update(tenantId, membershipId, body, version,
                actors.resolve(authentication.getName(), tenantId), command(idempotencyKey,
                        body + "|" + version));
        return new ApiEnvelope<>(response, RequestContext.meta(request));
    }

    @PostMapping("/{membershipId}/suspend")
    public ApiEnvelope<MembershipResponse> suspend(
            @PathVariable UUID tenantId,
            @PathVariable UUID membershipId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody ReasonRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        return changeStatus(tenantId, membershipId, MembershipStatus.SUSPENDED, body, idempotencyKey,
                ifMatch, authentication, request);
    }

    @PostMapping("/{membershipId}/reactivate")
    public ApiEnvelope<MembershipResponse> reactivate(
            @PathVariable UUID tenantId,
            @PathVariable UUID membershipId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody ReasonRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        return changeStatus(tenantId, membershipId, MembershipStatus.ACTIVE, body, idempotencyKey,
                ifMatch, authentication, request);
    }

    @PostMapping("/{membershipId}/revoke")
    public ApiEnvelope<MembershipResponse> revoke(
            @PathVariable UUID tenantId,
            @PathVariable UUID membershipId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody ReasonRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        return changeStatus(tenantId, membershipId, MembershipStatus.REVOKED, body, idempotencyKey,
                ifMatch, authentication, request);
    }

    @PostMapping("/{membershipId}/invitation-resends")
    public ResponseEntity<ApiEnvelope<InvitationDeliveryResponse>> resend(
            @PathVariable UUID tenantId,
            @PathVariable UUID membershipId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication,
            HttpServletRequest request) {
        InvitationDeliveryResponse response = memberships.resendInvitation(tenantId, membershipId,
                actors.resolve(authentication.getName(), tenantId), command(idempotencyKey, membershipId));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ApiEnvelope<>(response, RequestContext.meta(request)));
    }

    private ApiEnvelope<MembershipResponse> changeStatus(
            UUID tenantId,
            UUID membershipId,
            MembershipStatus status,
            ReasonRequest body,
            String idempotencyKey,
            String ifMatch,
            Authentication authentication,
            HttpServletRequest request) {
        long version = RequestContext.version(ifMatch);
        MembershipResponse response = memberships.changeStatus(tenantId, membershipId, status,
                body.reason(), version, actors.resolve(authentication.getName(), tenantId),
                command(idempotencyKey, status + "|" + body.reason() + "|" + version));
        return new ApiEnvelope<>(response, RequestContext.meta(request));
    }

    private static IdempotencyCommand command(String key, Object request) {
        return new IdempotencyCommand(RequestContext.idempotencyKey(key),
                RequestHasher.sha256(String.valueOf(request)));
    }
}
