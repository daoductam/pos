package com.pos.identityaccess.application;

import com.pos.identityaccess.application.exception.ForbiddenException;
import com.pos.identityaccess.application.model.ActorContext;
import com.pos.identityaccess.application.port.MembershipRepository;
import com.pos.identityaccess.domain.model.Membership;
import com.pos.identityaccess.domain.model.Role;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public final class ActorContextFactory {

    private final MembershipRepository memberships;

    public ActorContextFactory(MembershipRepository memberships) {
        this.memberships = Objects.requireNonNull(memberships, "memberships");
    }

    public ActorContext resolve(String subject, UUID tenantId) {
        try {
            var active = memberships.findActiveBySubjectAndTenant(subject, tenantId);
            if (active.isEmpty()) {
                throw new ForbiddenException();
            }
            Membership selected = active.stream()
                    .filter(membership -> membership.role() == Role.OWNER)
                    .findFirst()
                    .orElseGet(() -> active.stream()
                            .filter(membership -> membership.role() == Role.MANAGER)
                            .findFirst()
                            .orElseGet(() -> active.stream().findFirst().orElseThrow()));
            if (selected.role() == Role.OWNER) {
                return ActorContext.owner(subject, tenantId);
            }
            Set<UUID> branches = active.stream()
                    .filter(membership -> membership.role() == selected.role())
                    .map(Membership::branchId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return new ActorContext(subject, tenantId, selected.role(), branches);
        } catch (ForbiddenException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new com.pos.identityaccess.application.exception.AuthorizationUnavailableException();
        }
    }
}
