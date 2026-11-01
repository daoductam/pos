package com.pos.identityaccess.application.port;

import com.pos.identityaccess.application.model.AuthorizationScope;
import com.pos.identityaccess.domain.model.Membership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository {

    List<Membership> findVisibleBySubject(AuthorizationScope scope, UUID tenantId);

    List<Membership> findActiveBySubjectAndTenant(String subject, UUID tenantId);

    List<Membership> findActiveBySubject(String subject);

    List<Membership> findVisibleByTenant(AuthorizationScope scope, UUID tenantId);

    Optional<Membership> findVisibleById(AuthorizationScope scope, UUID tenantId, UUID membershipId);

    Optional<Membership> findById(UUID membershipId);

    Optional<Membership> findActiveByUserAndBranch(UUID tenantId, UUID userId, UUID branchId);

    Optional<Membership> findActiveTenantOwner(UUID tenantId, UUID userId);

    Optional<Membership> findCurrentByUserAndBranch(UUID tenantId, UUID userId, UUID branchId);

    Optional<Membership> findCurrentTenantOwner(UUID tenantId, UUID userId);

    List<Membership> findCurrentByUser(UUID userId);

    long countActiveOwners(UUID tenantId);

    Membership save(Membership membership);

    boolean updateIfVersionMatches(Membership membership, long expectedVersion);
}
