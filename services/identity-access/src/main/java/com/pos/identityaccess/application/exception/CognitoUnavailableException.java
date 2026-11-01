package com.pos.identityaccess.application.exception;

public class CognitoUnavailableException extends IdentityAccessException {

    private static final long serialVersionUID = 1L;

    public CognitoUnavailableException() {
        super(503, "cognito_unavailable", "The identity provider is temporarily unavailable");
    }
}
