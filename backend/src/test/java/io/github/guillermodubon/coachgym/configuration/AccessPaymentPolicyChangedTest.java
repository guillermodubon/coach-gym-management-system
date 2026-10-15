package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessPaymentPolicyChangedTest {

    private static final UUID ACTOR_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-15T20:00:00Z");

    @Test
    void rejectsAnEventThatDoesNotRepresentAChange() {
        assertThatThrownBy(() -> new AccessPaymentPolicyChanged(
                false, false, ACTOR_ID, "policy-admin", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingActorOrTimestamp() {
        assertThatThrownBy(() -> new AccessPaymentPolicyChanged(
                false, true, null, "policy-admin", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccessPaymentPolicyChanged(
                false, true, ACTOR_ID, " ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccessPaymentPolicyChanged(
                false, true, ACTOR_ID, "policy-admin", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
