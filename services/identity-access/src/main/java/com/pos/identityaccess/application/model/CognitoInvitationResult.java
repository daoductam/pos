package com.pos.identityaccess.application.model;

public record CognitoInvitationResult(Status status, String safeErrorCode) {

    public enum Status {
        SENT,
        RETRYABLE_FAILURE,
        PERMANENT_FAILURE
    }
}
