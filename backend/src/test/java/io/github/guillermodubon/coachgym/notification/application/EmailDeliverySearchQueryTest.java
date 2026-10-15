package io.github.guillermodubon.coachgym.notification.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EmailDeliverySearchQueryTest {

    @Test
    void rejectsAnInvertedOrOverlyBroadRequestedRange() {
        assertThatThrownBy(() -> new EmailDeliverySearchQuery(
                null, null, null, null,
                Instant.parse("2026-09-06T00:00:00Z"),
                Instant.parse("2026-09-05T00:00:00Z"),
                0, 25, null, null))
                .isInstanceOf(EmailDeliveryValidationException.class);

        assertThatThrownBy(() -> new EmailDeliverySearchQuery(
                null, null, null, null,
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                0, 25, null, null))
                .isInstanceOf(EmailDeliveryValidationException.class);
    }
}
