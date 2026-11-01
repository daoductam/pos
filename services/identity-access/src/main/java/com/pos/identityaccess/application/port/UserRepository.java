package com.pos.identityaccess.application.port;

import com.pos.identityaccess.domain.model.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findByCognitoSubject(String cognitoSubject);

    Optional<User> findById(UUID userId);

    Optional<User> findByNormalizedEmail(String normalizedEmail);

    User save(User user);

    boolean bindCognitoSubject(UUID userId, String cognitoSubject, String reconciliationStatus);
}
