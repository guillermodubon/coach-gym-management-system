package io.github.guillermodubon.coachgym.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.access.AccessAttemptRecorded;
import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessAttemptMetricsListenerTest {

    private static final UUID RECORD_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000001");
    private static final UUID CREDENTIAL_ID = UUID.fromString(
            "60000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString(
            "50000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-15T20:00:00Z");

    @Test
    void recordsOnlyBoundedQrDimensions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AccessAttemptMetricsListener listener =
                new AccessAttemptMetricsListener(registry);

        listener.record(new AccessAttemptRecorded(
                RECORD_ID,
                "QR_CREDENTIAL",
                "QR_CREDENTIAL",
                CREDENTIAL_ID,
                null,
                null,
                null,
                null,
                AccessResult.DENIED,
                AccessReasonCode.DUPLICATE_CHECK_IN,
                NOW,
                ACTOR_ID,
                "receptionist",
                NOW));

        assertThat(registry.get("coachgym.access.attempts")
                .tag("source", "QR_CREDENTIAL")
                .tag("result", "DENIED")
                .tag("duplicate", "true")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(registry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .noneMatch(tag -> tag.getValue().contains(RECORD_ID.toString())
                                || tag.getValue().contains(CREDENTIAL_ID.toString())));
    }
}
