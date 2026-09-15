package io.github.guillermodubon.coachgym.notification;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationSecurityApiIntegrationTest
        extends AbstractNotificationApiIntegrationTest {

    @Test
    void unauthenticatedInboxRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readMutationRequiresCsrf() throws Exception {
        UUID id = insertNotification(adminId, "SYSTEM", "INFO", null);
        mockMvc.perform(post("/api/v1/notifications/{id}/read", id)
                        .session(loginAsAdmin()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .session(loginAsAdmin()))
                .andExpect(status().isForbidden());
    }
}
