package com.pos.identityaccess.application.port;

import com.pos.identityaccess.application.model.OutboxEvent;
import com.pos.identityaccess.application.model.PublishResult;

public interface OutboxPublisher {

    PublishResult publish(OutboxEvent event);
}
