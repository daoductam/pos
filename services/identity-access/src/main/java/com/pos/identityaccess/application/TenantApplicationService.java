package com.pos.identityaccess.application;

import com.pos.identityaccess.application.exception.NotFoundInScopeException;
import com.pos.identityaccess.application.model.ActorContext;
import com.pos.identityaccess.application.model.AuthorizationScope;
import com.pos.identityaccess.application.port.TenantRepository;
import com.pos.identityaccess.api.dto.TenantResponse;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class TenantApplicationService {

    private final TenantRepository tenants;

    public TenantApplicationService(TenantRepository tenants) {
        this.tenants = Objects.requireNonNull(tenants, "tenants");
    }

    public TenantResponse get(UUID tenantId, ActorContext actor) {
        requireTenant(actor, tenantId);
        AuthorizationScope scope = new AuthorizationScope(actor.subject(), actor.tenantId(),
                actor.role(), actor.branchIds());
        return tenants.findById(scope, tenantId)
                .map(ResponseMapper::tenant)
                .orElseThrow(NotFoundInScopeException::new);
    }

    private static void requireTenant(ActorContext actor, UUID tenantId) {
        if (actor == null || tenantId == null || !tenantId.equals(actor.tenantId())) {
            throw new NotFoundInScopeException();
        }
    }
}
