package com.pos.identityaccess.application.model;

public record PublishResult(Status status, String safeErrorCode) {

    public enum Status {
        PUBLISHED,
        RETRYABLE_FAILURE,
        PERMANENT_FAILURE
    }
}
