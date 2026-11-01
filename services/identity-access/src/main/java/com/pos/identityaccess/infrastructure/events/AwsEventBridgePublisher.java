package com.pos.identityaccess.infrastructure.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pos.identityaccess.application.model.OutboxEvent;
import com.pos.identityaccess.application.model.PublishResult;
import com.pos.identityaccess.application.port.OutboxPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.EventBridgeException;

import java.util.Objects;

@Component
@ConditionalOnProperty(name = "app.events.publisher", havingValue = "aws")
public final class AwsEventBridgePublisher implements OutboxPublisher {

    private final EventBridgeClient client;
    private final ObjectMapper objectMapper;
    private final String eventBusName;
    private final String source;

    public AwsEventBridgePublisher(
            ObjectMapper objectMapper,
            @Value("${app.events.event-bus-name}") String eventBusName,
            @Value("${app.events.source:pos.identity-access}") String source,
            @Value("${app.events.region:${AWS_REGION:ap-southeast-1}}") String region) {
        this(EventBridgeClient.builder().region(Region.of(region)).build(), objectMapper,
                eventBusName, source);
    }

    AwsEventBridgePublisher(EventBridgeClient client, ObjectMapper objectMapper,
                            String eventBusName, String source) {
        this.client = Objects.requireNonNull(client, "client");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        if (eventBusName == null || eventBusName.isBlank()) {
            throw new IllegalArgumentException("eventBusName must not be blank");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        this.eventBusName = eventBusName;
        this.source = source;
    }

    @Override
    public PublishResult publish(OutboxEvent event) {
        try {
            PutEventsResponse response = client.putEvents(PutEventsRequest.builder()
                    .entries(PutEventsRequestEntry.builder()
                            .eventBusName(eventBusName)
                            .source(source)
                            .detailType(event.eventType())
                            .detail(detail(event))
                            .build())
                    .build());
            if (response.failedEntryCount() == null || response.failedEntryCount() == 0) {
                return new PublishResult(PublishResult.Status.PUBLISHED, null);
            }
            String errorCode = response.entries().isEmpty() || response.entries().getFirst().errorCode() == null
                    ? "EVENTBRIDGE_REJECTED" : response.entries().getFirst().errorCode();
            return new PublishResult(isPermanent(errorCode)
                    ? PublishResult.Status.PERMANENT_FAILURE
                    : PublishResult.Status.RETRYABLE_FAILURE, errorCode);
        } catch (EventBridgeException exception) {
            return new PublishResult(PublishResult.Status.RETRYABLE_FAILURE, "EVENTBRIDGE_UNAVAILABLE");
        } catch (JsonProcessingException exception) {
            return new PublishResult(PublishResult.Status.PERMANENT_FAILURE, "EVENT_PAYLOAD_INVALID");
        } catch (RuntimeException exception) {
            return new PublishResult(PublishResult.Status.RETRYABLE_FAILURE, "EVENTBRIDGE_UNAVAILABLE");
        }
    }

    private String detail(OutboxEvent event) throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("eventId", event.id().toString());
        root.put("eventVersion", event.eventVersion());
        root.put("eventType", event.eventType());
        root.put("aggregateType", event.aggregateType());
        root.put("aggregateId", event.aggregateId().toString());
        root.put("correlationId", event.correlationId());
        if (event.causationId() != null) {
            root.put("causationId", event.causationId());
        }
        root.put("occurredAt", event.occurredAt().toString());
        if (event.tenantId() != null) {
            root.put("tenantId", event.tenantId().toString());
        }
        if (event.branchId() != null) {
            root.put("branchId", event.branchId().toString());
        }
        root.set("payload", objectMapper.readTree(event.payload()));
        return objectMapper.writeValueAsString(root);
    }

    private static boolean isPermanent(String errorCode) {
        return "MalformedDetail".equals(errorCode)
                || "InvalidArgument".equals(errorCode)
                || "ResourceNotFoundException".equals(errorCode);
    }
}
