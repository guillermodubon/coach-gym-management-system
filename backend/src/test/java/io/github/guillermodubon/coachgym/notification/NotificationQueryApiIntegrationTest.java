package io.github.guillermodubon.coachgym.notification;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationQueryApiIntegrationTest
        extends AbstractNotificationApiIntegrationTest {

    @Test
    void adminListsOnlyOwnNotificationsWithFiltersAndPagination() throws Exception {
        UUID unreadId = insertNotification(
                adminId, "SYSTEM", "WARNING", null);
        insertNotification(
                adminId, "SYSTEM", "INFO",
                Instant.parse("2026-09-05T18:00:00Z"));
        insertNotification(secondAdminId, "SYSTEM", "WARNING", null);

        MockHttpSession admin = loginAsAdmin();
        mockMvc.perform(get("/api/v1/notifications")
                        .session(admin)
                        .param("read", "UNREAD")
                        .param("severity", "WARNING")
                        .param("type", "SYSTEM")
                        .param("page", "0")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(unreadId.toString()))
                .andExpect(jsonPath("$.items[0].recipientUserId")
                        .value(adminId.toString()))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void bothCurrentRolesCanReadTheirOwnInbox() throws Exception {
        insertNotification(adminId, "SYSTEM", "INFO", null);
        insertNotification(receptionistId, "SYSTEM", "INFO", null);

        mockMvc.perform(get("/api/v1/notifications")
                        .session(loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/notifications")
                        .session(loginAsReceptionist()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void rejectsUnknownAllowlistedValues() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .session(loginAsAdmin())
                        .param("sort", "database_column"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("NOTIFICATION_VALIDATION_FAILED"));
    }
}
