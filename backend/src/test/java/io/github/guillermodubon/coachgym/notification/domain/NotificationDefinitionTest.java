package io.github.guillermodubon.coachgym.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.notification.NotificationResourceType;
import io.github.guillermodubon.coachgym.notification.NotificationSeverity;
import io.github.guillermodubon.coachgym.notification.NotificationType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationDefinitionTest {

    @Test
    void buildsValidatedNotificationDefinition() {
        UUID recipient = UUID.randomUUID();
        UUID maintenance = UUID.randomUUID();
        NotificationDefinition definition = new NotificationDefinition(
                recipient, NotificationType.MAINTENANCE_ASSIGNED,
                NotificationSeverity.INFO, "Maintenance assigned",
                "A work order was assigned.",
                NotificationResourceType.MAINTENANCE, maintenance);
        assertThat(definition.recipientUserId()).isEqualTo(recipient);
        assertThat(definition.content().title()).isEqualTo("Maintenance assigned");
        assertThat(definition.reference().resourceId()).isEqualTo(maintenance);
    }

    @Test
    void defaultsNullReferenceToNoReference() {
        NotificationDefinition definition = new NotificationDefinition(
                UUID.randomUUID(), NotificationType.SYSTEM,
                NotificationSeverity.INFO,
                new NotificationContent("System notice", "System information."),
                null);
        assertThat(definition.reference().present()).isFalse();
    }

    @Test
    void rejectsMissingCoreFields() {
        NotificationContent content = new NotificationContent("Title", "Body");
        assertThatThrownBy(() -> new NotificationDefinition(
                null, NotificationType.SYSTEM, NotificationSeverity.INFO,
                content, NotificationReference.none()))
                .isInstanceOf(NotificationValidationException.class);
        assertThatThrownBy(() -> new NotificationDefinition(
                UUID.randomUUID(), null, NotificationSeverity.INFO,
                content, NotificationReference.none()))
                .isInstanceOf(NotificationValidationException.class);
        assertThatThrownBy(() -> new NotificationDefinition(
                UUID.randomUUID(), NotificationType.SYSTEM, null,
                content, NotificationReference.none()))
                .isInstanceOf(NotificationValidationException.class);
    }
}
