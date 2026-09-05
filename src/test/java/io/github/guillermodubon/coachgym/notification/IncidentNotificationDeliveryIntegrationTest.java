package io.github.guillermodubon.coachgym.notification;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentNotificationDeliveryIntegrationTest
        extends AbstractNotificationApiIntegrationTest {

    @Test
    void criticalIncidentNotifiesOtherActiveAdministrator() throws Exception {
        MockHttpSession reportingAdmin = loginAsAdmin();
        reportIncident(reportingAdmin, "CRITICAL", true, 0L);

        Integer delivered = jdbcTemplate.queryForObject("""
                select count(*) from gym.notifications
                where recipient_user_id = ?
                  and notification_type = 'INCIDENT_ASSIGNED'
                  and severity = 'CRITICAL'
                  and resource_type = 'INCIDENT'
                  and read_at is null
                """, Integer.class, secondAdminId);
        Integer actorNotifications = jdbcTemplate.queryForObject("""
                select count(*) from gym.notifications
                where recipient_user_id = ?
                  and notification_type = 'INCIDENT_ASSIGNED'
                """, Integer.class, adminId);

        assertThat(delivered).isEqualTo(1);
        assertThat(actorNotifications).isZero();
    }

    @Test
    void lowPriorityIncidentDoesNotCreateAdministratorAlert() throws Exception {
        reportIncident(loginAsAdmin(), "LOW", false, null);

        Integer delivered = jdbcTemplate.queryForObject(
                "select count(*) from gym.notifications",
                Integer.class);
        assertThat(delivered).isZero();
    }
}
