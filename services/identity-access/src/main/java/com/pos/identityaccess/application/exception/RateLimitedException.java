package com.pos.identityaccess.application.exception;

public class RateLimitedException extends IdentityAccessException {

    private static final long serialVersionUID = 1L;

    public RateLimitedException() {
        super(429, "rate_limited", "Too many requests");
    }
}
