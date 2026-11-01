package com.pos.identityaccess.application.port;

import com.pos.identityaccess.application.model.CognitoActivation;
import com.pos.identityaccess.application.model.CognitoInvitationRequest;
import com.pos.identityaccess.application.model.CognitoInvitationResult;
import com.pos.identityaccess.application.model.CognitoSubjectResolution;

public interface CognitoInvitationGateway {

    CognitoInvitationResult sendInvitation(CognitoInvitationRequest request);

    CognitoSubjectResolution resolveSubject(CognitoActivation activation);
}
