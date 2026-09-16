package io.github.guillermodubon.coachgym.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryLifecycleEvent;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryStatus;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmailDeliveryMetricsListenerTest {

    private static final UUID ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");

    @Test
    void recordsOnlyFiniteEmailDimensions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EmailDeliveryMetricsListener listener = new EmailDeliveryMetricsListener(registry);

        listener.record(new EmailDeliveryLifecycleEvent(
                ID,
                EmailDeliveryType.PAYMENT_RECEIPT,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "a***@example.com",
                EmailDeliveryStatus.FAILED,
                2,
                EmailDeliveryFailureCode.TRANSPORT_TIMEOUT,
                UUID.randomUUID(),
                "coach-admin",
                Instant.parse("2026-09-15T12:00:00Z")));

        assertThat(registry.get("coachgym.email.delivery.attempts")
                .tag("type", "PAYMENT_RECEIPT")
                .tag("result", "FAILED")
                .tag("retry", "true")
                .tag("failure_code", "TRANSPORT_TIMEOUT")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(registry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .noneMatch(tag -> tag.getValue().contains(ID.toString())
                                || tag.getValue().contains("example.com")));
    }
}
