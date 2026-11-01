package com.pos.identityaccess.application.exception;

public class IdempotencyConflictException extends ConflictException {

    private static final long serialVersionUID = 1L;

    public IdempotencyConflictException() {
        super("duplicate_request", "The idempotency key was already used with another request");
    }
}
