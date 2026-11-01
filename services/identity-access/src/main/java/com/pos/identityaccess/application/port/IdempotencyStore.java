package com.pos.identityaccess.application.port;

import com.pos.identityaccess.application.model.IdempotencyKey;
import com.pos.identityaccess.application.model.IdempotencyRecord;

import java.util.Optional;

public interface IdempotencyStore {

    Optional<IdempotencyRecord> find(IdempotencyKey key);

    IdempotencyRecord save(IdempotencyRecord record);
}
