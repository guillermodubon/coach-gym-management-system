package io.github.guillermodubon.coachgym.promotion.infrastructure.persistence;

import io.github.guillermodubon.coachgym.promotion.DiscountType;
import io.github.guillermodubon.coachgym.promotion.PromotionDetails;
import io.github.guillermodubon.coachgym.promotion.domain.PromotionDefinition;
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
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "gym", name = "promotions")
class PromotionJpaEntity {

    @Id
    private UUID id;

    @Column(
            name = "promotion_number",
            nullable = false,
            insertable = false,
            updatable = false)
    private Long promotionNumber;

    @Column(
            name = "promotion_code",
            nullable = false,
            insertable = false,
            updatable = false,
            length = 32)
    private String promotionCode;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(
            name = "discount_value",
            nullable = false,
            precision = 12,
            scale = 2)
    private BigDecimal discountValue;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 3)
    private String currency;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

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

    protected PromotionJpaEntity() {
    }

    static PromotionJpaEntity create(
            PromotionDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt) {

        PromotionJpaEntity entity = new PromotionJpaEntity();

        entity.id = UUID.randomUUID();
        entity.applyDefinition(definition);
        entity.active = true;
        entity.createdByUserId = actor.id();
        entity.updatedByUserId = actor.id();
        entity.createdAt = occurredAt;
        entity.updatedAt = occurredAt;

        return entity;
    }

    void updateDefinition(
            PromotionDefinition definition,
            AuthenticatedActor actor,
            Instant occurredAt) {

        applyDefinition(definition);
        updatedByUserId = actor.id();
        updatedAt = occurredAt;
    }

    void markEligibilityChanged(
            AuthenticatedActor actor,
            Instant occurredAt) {

        updatedByUserId = actor.id();
        updatedAt = occurredAt;
    }

    void changeActive(
            boolean newActive,
            AuthenticatedActor actor,
            Instant occurredAt) {

        active = newActive;
        updatedByUserId = actor.id();
        updatedAt = occurredAt;
    }

    long version() {
        return version;
    }

    boolean active() {
        return active;
    }

    private void applyDefinition(PromotionDefinition definition) {
        name = definition.name();
        description = definition.description();
        discountType = definition.discountType();
        discountValue = definition.discountValue();
        currency = definition.currency();
        validFrom = definition.validFrom();
        validUntil = definition.validUntil();
    }

    PromotionDetails toDetails() {
        return new PromotionDetails(
                id,
                promotionCode,
                name,
                description,
                discountType,
                discountValue,
                trimNullableCurrency(currency),
                validFrom,
                validUntil,
                active,
                createdAt,
                updatedAt,
                version);
    }

    private static String trimNullableCurrency(String value) {
        return value == null ? null : value.trim();
    }
}