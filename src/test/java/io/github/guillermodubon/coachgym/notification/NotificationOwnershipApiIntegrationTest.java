package io.github.guillermodubon.coachgym.notification;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationOwnershipApiIntegrationTest
        extends AbstractNotificationApiIntegrationTest {

    @Test
    void anotherUserCannotReadOrMutateNotification() throws Exception {
        UUID adminNotification = insertNotification(adminId, "SYSTEM", "INFO", null);
        MockHttpSession receptionist = loginAsReceptionist();

        mockMvc.perform(get("/api/v1/notifications/{id}", adminNotification)
                        .session(receptionist))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/notifications/{id}/read", adminNotification)
                        .session(receptionist).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

        assertThat(unreadCount(adminId)).isEqualTo(1);
        assertThat(readAt(adminNotification)).isNull();
    }
}
