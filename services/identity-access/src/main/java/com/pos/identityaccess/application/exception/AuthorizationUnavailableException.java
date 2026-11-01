package com.pos.identityaccess.application.exception;

public class AuthorizationUnavailableException extends IdentityAccessException {

    private static final long serialVersionUID = 1L;

    public AuthorizationUnavailableException() {
        super(503, "authorization_unavailable", "Current authorization could not be resolved");
    }
}
