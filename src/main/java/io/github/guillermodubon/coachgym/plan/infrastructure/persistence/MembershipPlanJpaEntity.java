package io.github.guillermodubon.coachgym.plan.infrastructure.persistence;

import io.github.guillermodubon.coachgym.plan.DurationUnit;
import io.github.guillermodubon.coachgym.plan.PlanDetails;
import io.github.guillermodubon.coachgym.plan.domain.PlanDefinition;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "gym", name = "membership_plans")
class MembershipPlanJpaEntity {

    @Id
    private UUID id;

    @Column(name = "plan_number", nullable = false, insertable = false, updatable = false)
    private Long planNumber;

    @Column(name = "plan_code", nullable = false, insertable = false, updatable = false, length = 32)
    private String planCode;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "duration_value", nullable = false)
    private short durationValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "duration_unit", nullable = false, length = 10)
    private DurationUnit durationUnit;

    @Column(name = "list_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal listPrice;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected MembershipPlanJpaEntity() {
    }

    static MembershipPlanJpaEntity create(
            PlanDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt) {
        MembershipPlanJpaEntity entity = new MembershipPlanJpaEntity();
        entity.id = UUID.randomUUID();
        entity.applyDefinition(definition);
        entity.active = true;
        entity.createdByUserId = actor.id();
        entity.updatedByUserId = actor.id();
        entity.createdAt = occurredAt;
        entity.updatedAt = occurredAt;
        return entity;
    }

    void update(
            PlanDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt) {
        applyDefinition(definition);
        updatedByUserId = actor.id();
        updatedAt = occurredAt;
    }

    void changeActive(boolean active, AuthenticatedActor actor, Instant occurredAt) {
        this.active = active;
        updatedByUserId = actor.id();
        updatedAt = occurredAt;
    }

    private void applyDefinition(PlanDefinition definition) {
        name = definition.name();
        description = definition.description();
        durationValue = (short) definition.durationValue();
        durationUnit = definition.durationUnit();
        listPrice = definition.listPrice();
        currency = definition.currency();
    }

    PlanDetails toDetails() {
        return new PlanDetails(
                id,
                planCode,
                name,
                description,
                durationValue,
                durationUnit,
                listPrice,
                currency == null ? null : currency.trim(),
                active,
                createdAt,
                updatedAt,
                version);
    }

    long version() {
        return version;
    }

    boolean active() {
        return active;
    }
}
