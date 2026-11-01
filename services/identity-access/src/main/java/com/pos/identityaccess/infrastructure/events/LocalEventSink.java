package com.pos.identityaccess.infrastructure.events;

import com.pos.identityaccess.application.model.OutboxEvent;
import com.pos.identityaccess.application.model.PublishResult;
import com.pos.identityaccess.application.port.OutboxPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "app.events.publisher", havingValue = "local", matchIfMissing = true)
public final class LocalEventSink implements OutboxPublisher {

    private final Map<UUID, OutboxEvent> events = new ConcurrentHashMap<>();

    @Override
    public PublishResult publish(OutboxEvent event) {
        events.putIfAbsent(event.id(), event);
        return new PublishResult(PublishResult.Status.PUBLISHED, null);
    }

    public List<OutboxEvent> events() {
        return List.copyOf(new ArrayList<>(events.values()));
    }

    public boolean contains(UUID eventId) {
        return events.containsKey(eventId);
    }
}
