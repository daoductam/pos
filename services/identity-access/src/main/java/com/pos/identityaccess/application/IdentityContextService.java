package com.pos.identityaccess.application;

import com.pos.identityaccess.api.dto.MeResponse;
import com.pos.identityaccess.application.exception.ForbiddenException;
import com.pos.identityaccess.application.port.MembershipRepository;
import com.pos.identityaccess.application.port.UserRepository;
import com.pos.identityaccess.domain.model.UserStatus;

import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public final class IdentityContextService {

    private final UserRepository users;
    private final MembershipRepository memberships;

    public IdentityContextService(UserRepository users, MembershipRepository memberships) {
        this.users = Objects.requireNonNull(users, "users");
        this.memberships = Objects.requireNonNull(memberships, "memberships");
    }

    public MeResponse me(String subject) {
        var user = users.findByCognitoSubject(subject).orElseThrow(ForbiddenException::new);
        if (user.status() != UserStatus.ACTIVE) {
            throw new ForbiddenException();
        }
        var responses = memberships.findActiveBySubject(subject).stream()
                .map(membership -> ResponseMapper.membership(membership, user, null))
                .toList();
        return new MeResponse(ResponseMapper.user(user), responses);
    }
}
