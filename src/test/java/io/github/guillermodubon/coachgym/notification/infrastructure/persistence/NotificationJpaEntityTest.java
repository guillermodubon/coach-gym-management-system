package io.github.guillermodubon.coachgym.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.notification.NotificationResourceType;
import io.github.guillermodubon.coachgym.notification.NotificationSeverity;
import io.github.guillermodubon.coachgym.notification.NotificationType;
import io.github.guillermodubon.coachgym.notification.domain.NotificationDefinition;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationJpaEntityTest {

    @Test
    void createsUnreadEntityAndMapsItToPublicDetails() {
        UUID recipient = UUID.randomUUID();
        UUID resource = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-05T13:00:00Z");
        NotificationJpaEntity entity = NotificationJpaEntity.create(
                new NotificationDefinition(
                        recipient,
                        NotificationType.INCIDENT_ASSIGNED,
                        NotificationSeverity.WARNING,
                        "Incident assigned",
                        "Incident INC-000001 requires attention.",
                        NotificationResourceType.INCIDENT,
                        resource),
                createdAt);

        var details = entity.toDetails();
        assertThat(details.id()).isNotNull();
        assertThat(details.recipientUserId()).isEqualTo(recipient);
        assertThat(details.read()).isFalse();
        assertThat(details.createdAt()).isEqualTo(createdAt);
        assertThat(details.version()).isZero();
    }

    @Test
    void marksAsReadOnlyOnceAndPreservesOriginalTimestamp() {
        NotificationJpaEntity entity = NotificationJpaEntity.create(
                new NotificationDefinition(
                        UUID.randomUUID(),
                        NotificationType.SYSTEM,
                        NotificationSeverity.INFO,
                        "System notice",
                        "System information.",
                        null,
                        null),
                Instant.parse("2026-09-05T13:00:00Z"));

        Instant first = Instant.parse("2026-09-05T14:00:00Z");
        Instant second = Instant.parse("2026-09-05T15:00:00Z");
        assertThat(entity.markAsRead(first)).isTrue();
        assertThat(entity.markAsRead(second)).isFalse();
        assertThat(entity.toDetails().readAt()).isEqualTo(first);
    }
}
