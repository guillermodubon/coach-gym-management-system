package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import io.github.guillermodubon.coachgym.client.ClientRegistered;
import io.github.guillermodubon.coachgym.plan.PlanChanged;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "gym", name = "audit_entries")
class AuditEntryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_identifier_snapshot", length = 100)
    private String actorIdentifierSnapshot;

    @Column(name = "action_code", nullable = false, length = 100)
    private String actionCode;

    @Column(name = "resource_type", nullable = false, length = 100)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "resource_code_snapshot", length = 64)
    private String resourceCodeSnapshot;

    @Column(columnDefinition = "text")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEntryJpaEntity() {
    }

    static AuditEntryJpaEntity from(ClientRegistered event) {
        AuditEntryJpaEntity entry = new AuditEntryJpaEntity();
        entry.id = UUID.randomUUID();
        entry.actorUserId = event.actorUserId();
        entry.actorIdentifierSnapshot = event.actorIdentifier();
        entry.actionCode = "CLIENT_REGISTERED";
        entry.resourceType = "CLIENT";
        entry.resourceId = event.clientId();
        entry.resourceCodeSnapshot = event.clientCode();
        entry.summary = "Client registered.";
        entry.metadata = Map.of();
        entry.occurredAt = event.occurredAt();
        return entry;
    }

    static AuditEntryJpaEntity from(PlanChanged event) {
        AuditEntryJpaEntity entry = new AuditEntryJpaEntity();
        entry.id = UUID.randomUUID();
        entry.actorUserId = event.actorUserId();
        entry.actorIdentifierSnapshot = event.actorIdentifier();
        entry.actionCode = "PLAN_" + event.changeType().name();
        entry.resourceType = "MEMBERSHIP_PLAN";
        entry.resourceId = event.planId();
        entry.resourceCodeSnapshot = event.planCode();
        entry.summary = switch (event.changeType()) {
            case CREATED -> "Membership plan created.";
            case UPDATED -> "Membership plan updated.";
            case DEACTIVATED -> "Membership plan deactivated.";
            case REACTIVATED -> "Membership plan reactivated.";
        };
        entry.metadata = Map.of();
        entry.occurredAt = event.occurredAt();
        return entry;
    }
}
