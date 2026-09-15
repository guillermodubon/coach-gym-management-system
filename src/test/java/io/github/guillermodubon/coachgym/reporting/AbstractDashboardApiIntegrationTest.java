package io.github.guillermodubon.coachgym.reporting;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;

abstract class AbstractDashboardApiIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @BeforeEach
    void cleanDashboardFixtures() {
        jdbcTemplate.update("delete from gym.notifications");
    }

    protected UUID insertUnreadNotification(UUID recipientUserId) {
        UUID id = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.ofInstant(
                Instant.parse("2026-09-05T18:00:00Z"),
                ZoneOffset.UTC);

        jdbcTemplate.update("""
                insert into gym.notifications
                    (id, recipient_user_id, notification_type, severity,
                     title, body, resource_type, resource_id, read_at,
                     created_at, updated_at, version)
                values (?, ?, 'SYSTEM', 'INFO', ?, ?, null, null, null,
                        ?, ?, 0)
                """,
                id,
                recipientUserId,
                "Dashboard notification",
                "Unread dashboard integration fixture.",
                occurredAt,
                occurredAt);
        return id;
    }

    protected void markNotificationRead(UUID notificationId) {
        OffsetDateTime readAt = OffsetDateTime.ofInstant(
                Instant.parse("2026-09-05T19:00:00Z"),
                ZoneOffset.UTC);
        jdbcTemplate.update("""
                update gym.notifications
                set read_at = ?, updated_at = ?
                where id = ?
                """,
                readAt,
                readAt,
                notificationId);
    }
}
