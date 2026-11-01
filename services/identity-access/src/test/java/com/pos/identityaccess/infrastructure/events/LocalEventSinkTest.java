package com.pos.identityaccess.infrastructure.events;

import com.pos.identityaccess.application.model.OutboxEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalEventSinkTest {

    @Test
    void shouldKeepStableEventIdAndSingleSideEffect_whenPublishedTwice() {
        LocalEventSink sink = new LocalEventSink();
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(eventId, UUID.randomUUID(), null,
                "MembershipChanged", 1, "Membership", UUID.randomUUID(),
                "{\"status\":\"ACTIVE\"}", "corr-1", "cause-1", Instant.now());

        sink.publish(event);
        sink.publish(event);

        assertThat(sink.events()).hasSize(1);
        assertThat(sink.events().getFirst().id()).isEqualTo(eventId);
    }
}
