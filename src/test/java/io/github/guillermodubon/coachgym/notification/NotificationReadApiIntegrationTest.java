package io.github.guillermodubon.coachgym.notification;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationReadApiIntegrationTest
        extends AbstractNotificationApiIntegrationTest {

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
}
