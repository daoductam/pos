package com.pos.identityaccess.application.model;

public record CognitoSubjectResolution(Status status, String subject) {

    public enum Status {
        MATCHED,
        PENDING,
        AMBIGUOUS,
        DRIFTED
    }
}
