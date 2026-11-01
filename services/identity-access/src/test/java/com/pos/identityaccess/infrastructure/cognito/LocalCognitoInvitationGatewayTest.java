package com.pos.identityaccess.infrastructure.cognito;

import com.pos.identityaccess.application.model.CognitoActivation;
import com.pos.identityaccess.application.model.CognitoInvitationRequest;
import com.pos.identityaccess.application.model.CognitoInvitationResult;
import com.pos.identityaccess.application.model.CognitoSubjectResolution;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalCognitoInvitationGatewayTest {

    @Test
    void shouldUseStableSubjectAndDeduplicateDelivery_whenInviteIsRetried() {
        LocalCognitoInvitationGateway gateway = new LocalCognitoInvitationGateway();
        UUID deliveryId = UUID.randomUUID();
        CognitoInvitationRequest request = new CognitoInvitationRequest(deliveryId,
                "cashier@example.com", "Cashier");

        assertThat(gateway.sendInvitation(request).status())
                .isEqualTo(CognitoInvitationResult.Status.SENT);
        String subject = gateway.subjectForEmail("cashier@example.com");
        assertThat(gateway.sendInvitation(request).status())
                .isEqualTo(CognitoInvitationResult.Status.SENT);
        assertThat(gateway.resolveSubject(new CognitoActivation(subject, "cashier@example.com")))
                .isEqualTo(new CognitoSubjectResolution(CognitoSubjectResolution.Status.MATCHED, subject));
    }

    @Test
    void shouldReportDrift_whenExternalSubjectDoesNotMatchEmail() {
        LocalCognitoInvitationGateway gateway = new LocalCognitoInvitationGateway();
        gateway.sendInvitation(new CognitoInvitationRequest(UUID.randomUUID(),
                "cashier@example.com", "Cashier"));

        assertThat(gateway.resolveSubject(new CognitoActivation("wrong-sub", "cashier@example.com"))
                .status()).isEqualTo(CognitoSubjectResolution.Status.DRIFTED);
    }
}
