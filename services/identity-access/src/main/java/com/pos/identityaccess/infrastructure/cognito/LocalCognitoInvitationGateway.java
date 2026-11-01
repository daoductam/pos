package com.pos.identityaccess.infrastructure.cognito;

import com.pos.identityaccess.application.model.CognitoActivation;
import com.pos.identityaccess.application.model.CognitoInvitationRequest;
import com.pos.identityaccess.application.model.CognitoInvitationResult;
import com.pos.identityaccess.application.model.CognitoSubjectResolution;
import com.pos.identityaccess.application.port.CognitoInvitationGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name = "app.cognito.adapter", havingValue = "local", matchIfMissing = true)
public final class LocalCognitoInvitationGateway implements CognitoInvitationGateway {

    private final Map<UUID, String> deliverySubjects = new ConcurrentHashMap<>();
    private final Map<String, String> emailSubjects = new ConcurrentHashMap<>();
    private final Set<String> permanentFailureEmails = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean retryableFailureNext = new AtomicBoolean();

    @Override
    public CognitoInvitationResult sendInvitation(CognitoInvitationRequest request) {
        if (retryableFailureNext.compareAndSet(true, false)) {
            return new CognitoInvitationResult(
                    CognitoInvitationResult.Status.RETRYABLE_FAILURE, "LOCAL_PROVIDER_TIMEOUT");
        }
        if (permanentFailureEmails.contains(request.normalizedEmail())) {
            return new CognitoInvitationResult(
                    CognitoInvitationResult.Status.PERMANENT_FAILURE, "LOCAL_INVALID_RECIPIENT");
        }
        String subject = emailSubjects.computeIfAbsent(request.normalizedEmail(),
                LocalCognitoInvitationGateway::deterministicSubject);
        deliverySubjects.putIfAbsent(request.deliveryId(), subject);
        return new CognitoInvitationResult(CognitoInvitationResult.Status.SENT, null);
    }

    @Override
    public CognitoSubjectResolution resolveSubject(CognitoActivation activation) {
        String knownSubject = emailSubjects.get(activation.normalizedEmail());
        if (knownSubject == null) {
            return new CognitoSubjectResolution(CognitoSubjectResolution.Status.PENDING,
                    activation.subject());
        }
        if (!knownSubject.equals(activation.subject())) {
            return new CognitoSubjectResolution(CognitoSubjectResolution.Status.DRIFTED,
                    knownSubject);
        }
        return new CognitoSubjectResolution(CognitoSubjectResolution.Status.MATCHED, knownSubject);
    }

    public void failNextAttempt() {
        retryableFailureNext.set(true);
    }

    public void failPermanentlyForEmail(String normalizedEmail) {
        permanentFailureEmails.add(normalizedEmail);
    }

    public String subjectForEmail(String normalizedEmail) {
        return emailSubjects.get(normalizedEmail);
    }

    private static String deterministicSubject(String normalizedEmail) {
        UUID value = UUID.nameUUIDFromBytes(
                ("local-cognito:" + normalizedEmail).getBytes(StandardCharsets.UTF_8));
        return "local-sub-" + value;
    }
}
