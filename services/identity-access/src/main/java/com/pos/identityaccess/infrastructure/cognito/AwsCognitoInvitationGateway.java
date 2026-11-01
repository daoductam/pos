package com.pos.identityaccess.infrastructure.cognito;

import com.pos.identityaccess.application.model.CognitoActivation;
import com.pos.identityaccess.application.model.CognitoInvitationRequest;
import com.pos.identityaccess.application.model.CognitoInvitationResult;
import com.pos.identityaccess.application.model.CognitoSubjectResolution;
import com.pos.identityaccess.application.port.CognitoInvitationGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeliveryMediumType;

import java.util.Objects;

@Component
@ConditionalOnProperty(name = "app.cognito.adapter", havingValue = "aws")
public final class AwsCognitoInvitationGateway implements CognitoInvitationGateway {

    private final CognitoIdentityProviderClient client;
    private final String userPoolId;

    public AwsCognitoInvitationGateway(
            @Value("${app.cognito.user-pool-id}") String userPoolId,
            @Value("${app.cognito.region:${AWS_REGION:ap-southeast-1}}") String region) {
        this(CognitoIdentityProviderClient.builder().region(Region.of(region)).build(), userPoolId);
    }

    AwsCognitoInvitationGateway(CognitoIdentityProviderClient client, String userPoolId) {
        this.client = Objects.requireNonNull(client, "client");
        if (userPoolId == null || userPoolId.isBlank()) {
            throw new IllegalArgumentException("userPoolId must not be blank");
        }
        this.userPoolId = userPoolId;
    }

    @Override
    public CognitoInvitationResult sendInvitation(CognitoInvitationRequest request) {
        try {
            client.adminCreateUser(AdminCreateUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(request.normalizedEmail())
                    .userAttributes(AttributeType.builder().name("email")
                            .value(request.normalizedEmail()).build(),
                            AttributeType.builder().name("name")
                                    .value(request.displayName()).build())
                    .desiredDeliveryMediums(DeliveryMediumType.EMAIL)
                    .build());
            return new CognitoInvitationResult(CognitoInvitationResult.Status.SENT, null);
        } catch (CognitoIdentityProviderException exception) {
            if ("UsernameExistsException".equals(errorCode(exception))) {
                return new CognitoInvitationResult(CognitoInvitationResult.Status.SENT, null);
            }
            return new CognitoInvitationResult(classify(exception), safeErrorCode(exception));
        } catch (RuntimeException exception) {
            return new CognitoInvitationResult(
                    CognitoInvitationResult.Status.RETRYABLE_FAILURE, "COGNITO_UNAVAILABLE");
        }
    }

    @Override
    public CognitoSubjectResolution resolveSubject(CognitoActivation activation) {
        try {
            String providerSubject = client.adminGetUser(AdminGetUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(activation.normalizedEmail())
                    .build()).userAttributes().stream()
                    .filter(attribute -> "sub".equals(attribute.name()))
                    .findFirst()
                    .map(AttributeType::value)
                    .orElse(null);
            if (providerSubject == null) {
                return new CognitoSubjectResolution(CognitoSubjectResolution.Status.PENDING,
                        activation.subject());
            }
            return providerSubject.equals(activation.subject())
                    ? new CognitoSubjectResolution(CognitoSubjectResolution.Status.MATCHED, providerSubject)
                    : new CognitoSubjectResolution(CognitoSubjectResolution.Status.DRIFTED, providerSubject);
        } catch (CognitoIdentityProviderException exception) {
            if ("UserNotFoundException".equals(errorCode(exception))) {
                return new CognitoSubjectResolution(CognitoSubjectResolution.Status.PENDING,
                        activation.subject());
            }
            return new CognitoSubjectResolution(CognitoSubjectResolution.Status.PENDING,
                    activation.subject());
        } catch (RuntimeException exception) {
            return new CognitoSubjectResolution(CognitoSubjectResolution.Status.PENDING,
                    activation.subject());
        }
    }

    private static CognitoInvitationResult.Status classify(CognitoIdentityProviderException exception) {
        int status = exception.statusCode();
        return status == 400 && exception.awsErrorDetails() != null
                && errorCode(exception) != null
                && errorCode(exception).endsWith("InvalidParameterException")
                ? CognitoInvitationResult.Status.PERMANENT_FAILURE
                : CognitoInvitationResult.Status.RETRYABLE_FAILURE;
    }

    private static String safeErrorCode(CognitoIdentityProviderException exception) {
        String code = errorCode(exception);
        return code == null || code.isBlank() ? "COGNITO_FAILURE" : code;
    }

    private static String errorCode(CognitoIdentityProviderException exception) {
        return exception.awsErrorDetails() == null ? null : exception.awsErrorDetails().errorCode();
    }

}
