package com.pos.identityaccess.application.exception;

public class NotFoundInScopeException extends IdentityAccessException {

    private static final long serialVersionUID = 1L;

    public NotFoundInScopeException() {
        super(404, "not_found", "The resource does not exist in the authorized scope");
    }
}
