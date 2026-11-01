package com.pos.identityaccess.application;

import com.pos.identityaccess.application.exception.AuthorizationUnavailableException;
import com.pos.identityaccess.application.model.AuthorizationDecision;
import com.pos.identityaccess.application.model.AuthorizationRequest;
import com.pos.identityaccess.application.port.BranchRepository;
import com.pos.identityaccess.application.port.MembershipRepository;
import com.pos.identityaccess.application.port.UserRepository;
import com.pos.identityaccess.domain.model.BranchStatus;
import com.pos.identityaccess.domain.model.Membership;
import com.pos.identityaccess.domain.model.Role;
import com.pos.identityaccess.domain.model.UserStatus;
import com.pos.identityaccess.observability.MetricsConfiguration.SecurityMetrics;

import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class AuthorizationDecisionService {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final BranchRepository branches;
    private final SecurityMetrics metrics;

    public AuthorizationDecisionService(
            UserRepository users,
            MembershipRepository memberships,
            BranchRepository branches) {
        this(users, memberships, branches, SecurityMetrics.noop());
    }

    @Autowired
    public AuthorizationDecisionService(
            UserRepository users,
            MembershipRepository memberships,
            BranchRepository branches,
            SecurityMetrics metrics) {
        this.users = Objects.requireNonNull(users, "users");
        this.memberships = Objects.requireNonNull(memberships, "memberships");
        this.branches = Objects.requireNonNull(branches, "branches");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public AuthorizationDecision decide(AuthorizationRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            var user = users.findByCognitoSubject(request.subject());
            if (user.isEmpty() || user.get().status() != UserStatus.ACTIVE) {
                return denied(request, "NO_IDENTITY");
            }
            var scopedMemberships = memberships.findActiveBySubjectAndTenant(
                            request.subject(), request.tenantId())
                    .stream()
                    .filter(membership -> matchesScope(membership, request))
                    .toList();
            if (scopedMemberships.isEmpty()) {
                return denied(request, "INVALID_SCOPE");
            }
            if (request.branchId() != null) {
                var branch = branches.findByTenantAndId(request.tenantId(), request.branchId());
                if (branch.isEmpty() || branch.get().status() != BranchStatus.ACTIVE) {
                    return denied(request, "BRANCH_UNAVAILABLE");
                }
            }
            return scopedMemberships.stream()
                    .filter(membership -> PermissionCatalog.forRole(membership.role())
                            .contains(request.requiredPermission()))
                    .findFirst()
                    .map(membership -> allowed(request, membership))
                    .orElseGet(() -> denied(request, "FORBIDDEN"));
        } catch (RuntimeException exception) {
            if (exception instanceof com.pos.identityaccess.application.exception.IdentityAccessException) {
                throw exception;
            }
            throw new AuthorizationUnavailableException();
        }
    }

    private AuthorizationDecision denied(AuthorizationRequest request, String reason) {
        metrics.recordAuthorizationDenied();
        return AuthorizationDecision.denied(request, reason);
    }

    private static boolean matchesScope(Membership membership, AuthorizationRequest request) {
        if (request.branchId() == null) {
            return membership.role() == Role.OWNER && membership.branchId() == null;
        }
        return membership.branchId() != null && membership.branchId().equals(request.branchId());
    }

    private static AuthorizationDecision allowed(
            AuthorizationRequest request, Membership membership) {
        Set<String> permissions = PermissionCatalog.forRole(membership.role());
        return new AuthorizationDecision(true, "ALLOWED", request.subject(), request.tenantId(),
                request.branchId(), membership.role(), permissions, membership.version(),
                request.branchId() == null ? "TENANT" : "BRANCH");
    }
}
