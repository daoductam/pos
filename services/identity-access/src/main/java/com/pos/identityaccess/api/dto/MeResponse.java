package com.pos.identityaccess.api.dto;

import java.util.List;

public record MeResponse(UserResponse user, List<MembershipResponse> memberships) {

    public MeResponse {
        memberships = List.copyOf(memberships);
    }
}
