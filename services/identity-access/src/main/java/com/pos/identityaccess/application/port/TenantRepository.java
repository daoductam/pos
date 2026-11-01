package com.pos.identityaccess.application.port;

import com.pos.identityaccess.application.model.AuthorizationScope;
import com.pos.identityaccess.domain.model.Tenant;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository {

    Optional<Tenant> findById(AuthorizationScope scope, UUID tenantId);

    Tenant save(Tenant tenant);
}
