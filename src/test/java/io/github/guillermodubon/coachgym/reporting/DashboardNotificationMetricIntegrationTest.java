package io.github.guillermodubon.coachgym.reporting;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardNotificationMetricIntegrationTest
        extends AbstractDashboardApiIntegrationTest {

    @Test
    void countsOnlyUnreadNotificationsOwnedByAuthenticatedActor() throws Exception {
        UUID unread = insertUnreadNotification(adminId);
        insertUnreadNotification(adminId);
        insertUnreadNotification(receptionistId);
        UUID read = insertUnreadNotification(adminId);
        markNotificationRead(read);

        mockMvc.perform(get("/api/v1/reporting/dashboard")
                        .session(loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.unread").value(2));

        mockMvc.perform(get("/api/v1/reporting/dashboard")
                        .session(loginAsReceptionist()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.unread").value(1));

        org.assertj.core.api.Assertions.assertThat(unread).isNotNull();
    }
}
