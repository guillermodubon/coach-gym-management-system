package io.github.guillermodubon.coachgym.notification;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationReadApiIntegrationTest
        extends AbstractNotificationApiIntegrationTest {

    private static final UUID INITIAL_BRANCH_ID =
            UUID.fromString("7b0bf7d5-5184-43d2-8f9a-200000000002");

    @Test
    void marksOneNotificationAsReadIdempotently() throws Exception {
        UUID id = insertNotification(adminId, "SYSTEM", "INFO", null);
        MockHttpSession admin = loginAsAdmin();

        mockMvc.perform(post("/api/v1/notifications/{id}/read", id)
                        .session(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        Instant firstReadAt = readAt(id);

        mockMvc.perform(post("/api/v1/notifications/{id}/read", id)
                        .session(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        assertThat(readAt(id)).isEqualTo(firstReadAt);
        assertThat(unreadCount(adminId)).isZero();
    }

    @Test
    void marksAllOwnNotificationsWithoutTouchingAnotherInbox() throws Exception {
        insertNotification(adminId, "SYSTEM", "INFO", null);
        insertNotification(adminId, "SYSTEM", "WARNING", null);
        UUID otherId = insertNotification(secondAdminId, "SYSTEM", "INFO", null);

        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .session(loginAsAdmin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        assertThat(unreadCount(adminId)).isZero();
        assertThat(unreadCount(secondAdminId)).isEqualTo(1);
        assertThat(readAt(otherId)).isNull();
    }

    @Test
    void returnsCurrentUnreadCount() throws Exception {
        insertNotification(receptionistId, "SYSTEM", "INFO", null);
        insertNotification(receptionistId, "SYSTEM", "INFO",
                Instant.parse("2026-09-05T18:00:00Z"));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .session(loginAsReceptionist()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void unreadCountAndReadAccessStayWithinActiveBranchAndIncludeGlobalNotices()
            throws Exception {
        UUID otherBranchId = UUID.randomUUID();
        String branchCode = "NOTICE_" + otherBranchId.toString()
                .replace("-", "").substring(0, 8).toUpperCase();
        jdbcTemplate.update("""
                insert into gym.gym_branches
                    (id, organization_id, code, name, timezone, status, version)
                values (?, '7b0bf7d5-5184-43d2-8f9a-200000000001', ?,
                        'Notification Branch', 'America/El_Salvador', 'ACTIVE', 0)
                """, otherBranchId, branchCode);

        UUID globalNotificationId = insertNotification(
                adminId, "SYSTEM", "INFO", null);
        UUID activeBranchNotificationId = insertNotification(
                adminId, "SYSTEM", "INFO", null, INITIAL_BRANCH_ID);
        UUID otherBranchNotificationId = insertNotification(
                adminId, "SYSTEM", "WARNING", null, otherBranchId);

        try {
            var activeBranchSession = loginAsAdmin();
            mockMvc.perform(get("/api/v1/notifications/unread-count")
                            .session(activeBranchSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(2));

            mockMvc.perform(post(
                            "/api/v1/notifications/{id}/read",
                            otherBranchNotificationId)
                            .session(activeBranchSession).with(csrf()))
                    .andExpect(status().isNotFound());

            assertThat(readAt(otherBranchNotificationId)).isNull();
            assertThat(readAt(globalNotificationId)).isNull();
            assertThat(readAt(activeBranchNotificationId)).isNull();
        } finally {
            jdbcTemplate.update("delete from gym.notifications where id in (?, ?, ?)",
                    globalNotificationId,
                    activeBranchNotificationId,
                    otherBranchNotificationId);
            jdbcTemplate.update("""
                    update gym.gym_branches
                    set status = 'INACTIVE', version = version + 1
                    where id = ?
                    """, otherBranchId);
        }
    }
}
