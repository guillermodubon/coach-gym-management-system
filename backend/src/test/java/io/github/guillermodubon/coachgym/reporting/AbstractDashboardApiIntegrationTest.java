package io.github.guillermodubon.coachgym.reporting;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class AbstractDashboardApiIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @BeforeEach
    void cleanDashboardFixtures() {
        jdbcTemplate.update("delete from gym.notifications");
        normalizeReportingScopes();
    }

    @AfterEach
    void restoreReportingStaffScopes() {
        normalizeReportingScopes();
    }

    private void normalizeReportingScopes() {
        if (adminId != null) {
            jdbcTemplate.update("""
                    update gym.staff_scopes
                    set scope_type = 'ORGANIZATION', version = version + 1
                    where user_id = ? and scope_type <> 'ORGANIZATION'
                    """, adminId);
        }
        if (receptionistId != null) {
            jdbcTemplate.update("""
                    update gym.staff_scopes
                    set scope_type = 'BRANCH', version = version + 1
                    where user_id = ? and scope_type <> 'BRANCH'
                    """, receptionistId);
        }
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
