package io.github.guillermodubon.coachgym.notification;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationDatabaseConstraintIntegrationTest
        extends AbstractNotificationApiIntegrationTest {

    @Test
    void databaseRejectsUnsupportedTypeAndPartialResourceReference() {
        OffsetDateTime occurredAt =
                OffsetDateTime.ofInstant(
                        Instant.parse(
                                "2026-09-05T18:00:00Z"),
                        ZoneOffset.UTC);

        assertThatThrownBy(
                () -> jdbcTemplate.update(
                        """
                        insert into gym.notifications
                            (
                                id,
                                recipient_user_id,
                                notification_type,
                                severity,
                                title,
                                body,
                                resource_type,
                                resource_id,
                                created_at,
                                updated_at,
                                version
                            )
                        values (
                            ?,
                            ?,
                            'UNKNOWN',
                            'INFO',
                            'Title',
                            'Body',
                            null,
                            null,
                            ?,
                            ?,
                            0
                        )
                        """,
                        UUID.randomUUID(),
                        adminId,
                        occurredAt,
                        occurredAt))
                .isInstanceOf(
                        DataIntegrityViolationException.class);

        assertThatThrownBy(
                () -> jdbcTemplate.update(
                        """
                        insert into gym.notifications
                            (
                                id,
                                recipient_user_id,
                                notification_type,
                                severity,
                                title,
                                body,
                                resource_type,
                                resource_id,
                                created_at,
                                updated_at,
                                version
                            )
                        values (
                            ?,
                            ?,
                            'SYSTEM',
                            'INFO',
                            'Title',
                            'Body',
                            'SYSTEM',
                            null,
                            ?,
                            ?,
                            0
                        )
                        """,
                        UUID.randomUUID(),
                        adminId,
                        occurredAt,
                        occurredAt))
                .isInstanceOf(
                        DataIntegrityViolationException.class);
    }
}