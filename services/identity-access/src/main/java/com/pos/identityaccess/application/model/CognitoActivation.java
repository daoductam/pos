package com.pos.identityaccess.application.model;

public record CognitoActivation(String subject, String normalizedEmail) {

    public CognitoActivation {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("normalizedEmail must not be blank");
        }
    }
}
