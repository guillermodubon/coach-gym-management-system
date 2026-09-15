package io.github.guillermodubon.coachgym.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationPublicContractTest {

    @Test
    void derivesUnreadStateFromMissingReadTimestamp() {
        NotificationDetails details = notification(null);
        assertThat(details.read()).isFalse();
        assertThat(details.resourceType())
                .isEqualTo(NotificationResourceType.MAINTENANCE);
    }

    @Test
    void derivesReadStateFromReadTimestamp() {
        NotificationDetails details = notification(
                Instant.parse("2026-09-05T12:30:00Z"));
        assertThat(details.read()).isTrue();
    }

    @Test
    void acceptsZeroUnreadNotifications() {
        assertThat(new NotificationUnreadCount(0).count()).isZero();
    }

    @Test
    void rejectsNegativeUnreadCount() {
        assertThatThrownBy(() -> new NotificationUnreadCount(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unread notification count must not be negative.");
    }

    private static NotificationDetails notification(Instant readAt) {
        Instant createdAt = Instant.parse("2026-09-05T12:00:00Z");
        return new NotificationDetails(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                NotificationType.MAINTENANCE_ASSIGNED,
                NotificationSeverity.INFO,
                "Maintenance assigned",
                "Maintenance MNT-000001 was assigned.",
                NotificationResourceType.MAINTENANCE,
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                readAt,
                createdAt,
                createdAt,
                0L);
    }
}
