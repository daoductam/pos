package com.pos.identityaccess.application.exception;

public class ConflictException extends IdentityAccessException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String code, String message) {
        super(409, code, message);
    }
}
