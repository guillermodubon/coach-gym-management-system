package io.github.guillermodubon.coachgym.notification.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.notification.NotificationDetails;
import io.github.guillermodubon.coachgym.notification.NotificationResourceType;
import io.github.guillermodubon.coachgym.notification.NotificationSeverity;
import io.github.guillermodubon.coachgym.notification.NotificationType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationResponseTest {

    @Test
    void mapsReadStateAndRecipientScopedDetails() {
        Instant now = Instant.parse("2026-09-05T18:00:00Z");
        UUID recipient = UUID.randomUUID();
        NotificationResponse response = NotificationResponse.from(
                new NotificationDetails(
                        UUID.randomUUID(),
                        recipient,
                        NotificationType.MAINTENANCE_ASSIGNED,
                        NotificationSeverity.WARNING,
                        "Maintenance completed",
                        "Equipment remains out of service.",
                        NotificationResourceType.MAINTENANCE,
                        UUID.randomUUID(),
                        now,
                        now.minusSeconds(60),
                        now,
                        1L));

        assertThat(response.recipientUserId()).isEqualTo(recipient);
        assertThat(response.read()).isTrue();
        assertThat(response.readAt()).isEqualTo(now);
    }
}
