package com.pos.identityaccess.application.exception;

public class OptimisticConflictException extends ConflictException {

    private static final long serialVersionUID = 1L;

    public OptimisticConflictException() {
        super("conflict", "The resource was changed by another request");
    }
}
