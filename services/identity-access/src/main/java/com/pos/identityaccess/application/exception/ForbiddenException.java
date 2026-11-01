package com.pos.identityaccess.application.exception;

public class ForbiddenException extends IdentityAccessException {

    private static final long serialVersionUID = 1L;

    public ForbiddenException() {
        super(403, "forbidden", "The caller is not allowed to access this resource");
    }
}
