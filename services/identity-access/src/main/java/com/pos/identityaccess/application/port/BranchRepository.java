package com.pos.identityaccess.application.port;

import com.pos.identityaccess.application.model.AuthorizationScope;
import com.pos.identityaccess.domain.model.Branch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchRepository {

    List<Branch> findVisibleByTenant(AuthorizationScope scope, UUID tenantId);

    Optional<Branch> findVisibleById(AuthorizationScope scope, UUID tenantId, UUID branchId);

    Optional<Branch> findByTenantAndId(UUID tenantId, UUID branchId);

    Branch save(Branch branch);

    boolean updateIfVersionMatches(Branch branch, long expectedVersion);
}
