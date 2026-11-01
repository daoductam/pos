package com.pos.identityaccess.api.dto;

public record TenantBootstrapResponse(
        TenantResponse tenant,
        MembershipResponse ownerMembership) {
}
