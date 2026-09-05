package io.github.guillermodubon.coachgym.notification.infrastructure.persistence;

import io.github.guillermodubon.coachgym.notification.NotificationDetails;
import io.github.guillermodubon.coachgym.notification.NotificationResourceType;
import io.github.guillermodubon.coachgym.notification.NotificationSeverity;
import io.github.guillermodubon.coachgym.notification.NotificationType;
import io.github.guillermodubon.coachgym.notification.domain.NotificationDefinition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notifications", schema = "gym")
class NotificationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 40, updatable = false)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10, updatable = false)
    private NotificationSeverity severity;

    @Column(name = "title", nullable = false, length = 160, updatable = false)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "text", updatable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 100, updatable = false)
    private NotificationResourceType resourceType;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected NotificationJpaEntity() {
    }

    static NotificationJpaEntity create(
            NotificationDefinition definition,
            Instant createdAt) {
        Objects.requireNonNull(definition, "Notification definition is required.");
        Objects.requireNonNull(createdAt, "Notification creation timestamp is required.");

        NotificationJpaEntity entity = new NotificationJpaEntity();
        entity.id = UUID.randomUUID();
        entity.recipientUserId = definition.recipientUserId();
        entity.notificationType = definition.notificationType();
        entity.severity = definition.severity();
        entity.title = definition.content().title();
        entity.body = definition.content().body();
        entity.resourceType = definition.reference().resourceType();
        entity.resourceId = definition.reference().resourceId();
        entity.readAt = null;
        entity.createdAt = createdAt;
        entity.updatedAt = createdAt;
        entity.version = 0L;
        return entity;
    }

    boolean markAsRead(Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "Notification read timestamp is required.");
        if (readAt != null) {
            return false;
        }
        readAt = occurredAt;
        updatedAt = occurredAt;
        return true;
    }

    UUID id() {
        return id;
    }

    UUID recipientUserId() {
        return recipientUserId;
    }

    NotificationDetails toDetails() {
        return new NotificationDetails(
                id,
                recipientUserId,
                notificationType,
                severity,
                title,
                body,
                resourceType,
                resourceId,
                readAt,
                createdAt,
                updatedAt,
                version);
    }
}
