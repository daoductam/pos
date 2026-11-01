package com.pos.identityaccess.application.exception;

public class ValidationException extends IdentityAccessException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(422, "validation_error", message);
    }
}
