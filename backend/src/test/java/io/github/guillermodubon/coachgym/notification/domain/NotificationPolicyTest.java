package io.github.guillermodubon.coachgym.notification.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.notification.NotificationResourceType;
import io.github.guillermodubon.coachgym.notification.NotificationSeverity;
import io.github.guillermodubon.coachgym.notification.NotificationType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationPolicyTest {

    private final NotificationPolicy policy = new NotificationPolicy();

    @Test
    void acceptsAssignedMaintenanceNotification() {
        NotificationDefinition definition = new NotificationDefinition(
                UUID.randomUUID(), NotificationType.MAINTENANCE_ASSIGNED,
                NotificationSeverity.INFO, "Maintenance assigned",
                "A maintenance work order was assigned.",
                NotificationResourceType.MAINTENANCE, UUID.randomUUID());
        assertThatCode(() -> policy.validate(definition)).doesNotThrowAnyException();
    }

    @Test
    void rejectsSystemNotificationWithBusinessReference() {
        NotificationDefinition definition = new NotificationDefinition(
                UUID.randomUUID(), NotificationType.SYSTEM,
                NotificationSeverity.INFO, "System notice", "System information.",
                NotificationResourceType.INCIDENT, UUID.randomUUID());
        assertThatThrownBy(() -> policy.validate(definition))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessage("System notifications must not reference a business resource.");
    }

    @Test
    void rejectsCriticalMembershipExpirationNotification() {
        NotificationDefinition definition = new NotificationDefinition(
                UUID.randomUUID(), NotificationType.MEMBERSHIP_EXPIRING,
                NotificationSeverity.CRITICAL, "Membership expiring",
                "A membership is approaching its expiration date.",
                NotificationResourceType.MEMBERSHIP, UUID.randomUUID());
        assertThatThrownBy(() -> policy.validate(definition))
                .isInstanceOf(NotificationValidationException.class);
    }
}
