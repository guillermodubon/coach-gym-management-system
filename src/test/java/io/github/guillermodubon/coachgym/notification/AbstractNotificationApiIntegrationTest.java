package io.github.guillermodubon.coachgym.notification;

import com.jayway.jsonpath.JsonPath;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class AbstractNotificationApiIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    protected static final String SECOND_ADMIN_USERNAME = "notification-admin";
    protected static final String SECOND_ADMIN_PASSWORD = "Notification-strong-password";

    protected UUID secondAdminId;

    @BeforeEach
    void setUpNotificationFixtures() {
        jdbcTemplate.update("delete from gym.notifications");
        secondAdminId = provisionUser(
                SECOND_ADMIN_USERNAME,
                "notification-admin@example.test",
                SECOND_ADMIN_PASSWORD,
                "ADMIN");
    }

    protected MockHttpSession loginAsSecondAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(
                                SECOND_ADMIN_USERNAME,
                                SECOND_ADMIN_PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    protected UUID insertNotification(
            UUID recipientUserId,
            String notificationType,
            String severity,
            Instant readAt) {

        UUID id = UUID.randomUUID();

        OffsetDateTime fixtureTimestamp =
                OffsetDateTime.ofInstant(
                        Instant.parse("2026-09-05T17:00:00Z"),
                        ZoneOffset.UTC);

        OffsetDateTime readTimestamp =
                readAt == null
                        ? null
                        : OffsetDateTime.ofInstant(
                        readAt,
                        ZoneOffset.UTC);

        jdbcTemplate.update(
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
                        read_at,
                        created_at,
                        updated_at,
                        version
                    )
                values (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    'SYSTEM',
                    ?,
                    ?,
                    ?,
                    ?,
                    0
                )
                """,
                id,
                recipientUserId,
                notificationType,
                severity,
                "Integration notification",
                "Notification inbox integration fixture.",
                UUID.randomUUID(),
                readTimestamp,
                fixtureTimestamp,
                fixtureTimestamp);

        return id;
    }

    protected UUID responseId(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.read(
                result.getResponse().getContentAsString(), "$.id"));
    }

    protected long unreadCount(UUID recipientUserId) {
        return jdbcTemplate.queryForObject("""
                select count(*) from gym.notifications
                where recipient_user_id = ? and read_at is null
                """, Long.class, recipientUserId);
    }

    protected Instant readAt(UUID notificationId) {
        return jdbcTemplate.queryForObject(
                """
                select read_at
                from gym.notifications
                where id = ?
                """,
                (resultSet, rowNumber) -> {
                    OffsetDateTime value =
                            resultSet.getObject(
                                    "read_at",
                                    OffsetDateTime.class);

                    return value == null
                            ? null
                            : value.toInstant();
                },
                notificationId);
    }
}
