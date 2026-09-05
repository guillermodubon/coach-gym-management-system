package io.github.guillermodubon.coachgym.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationTypeTest {

    @Test
    void exposesOnlyDatabaseSupportedTypes() {
        assertThat(NotificationType.values())
                .containsExactly(
                        NotificationType.MEMBERSHIP_EXPIRING,
                        NotificationType.PAYMENT_VOIDED,
                        NotificationType.PAYMENT_REFUNDED,
                        NotificationType.INCIDENT_ASSIGNED,
                        NotificationType.MAINTENANCE_ASSIGNED,
                        NotificationType.SYSTEM);
    }
}
