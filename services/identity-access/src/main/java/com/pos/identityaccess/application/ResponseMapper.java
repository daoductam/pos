package com.pos.identityaccess.application;

import com.pos.identityaccess.api.dto.BranchResponse;
import com.pos.identityaccess.api.dto.MembershipResponse;
import com.pos.identityaccess.api.dto.TenantResponse;
import com.pos.identityaccess.api.dto.UserResponse;
import com.pos.identityaccess.domain.model.Branch;
import com.pos.identityaccess.domain.model.Membership;
import com.pos.identityaccess.domain.model.Tenant;
import com.pos.identityaccess.domain.model.User;

public final class ResponseMapper {

    private ResponseMapper() {
    }

    public static TenantResponse tenant(Tenant tenant) {
        return new TenantResponse(tenant.id(), tenant.name(), tenant.status(), tenant.version(),
                tenant.createdAt(), tenant.updatedAt());
    }

    public static BranchResponse branch(Branch branch) {
        return new BranchResponse(branch.id(), branch.tenantId(), branch.code(), branch.name(),
                branch.address(), branch.status(), branch.version(), branch.createdAt(),
                branch.updatedAt());
    }

    public static UserResponse user(User user) {
        return new UserResponse(user.id(), user.normalizedEmail(), user.displayName(), user.phone(),
                user.status());
    }

    public static MembershipResponse membership(Membership membership, User user, String invitationStatus) {
        return new MembershipResponse(membership.id(), membership.tenantId(), membership.branchId(),
                membership.userId(), user == null ? null : user.normalizedEmail(), membership.role(),
                membership.status(), invitationStatus, PermissionCatalog.forRole(membership.role()),
                membership.version());
    }
}
