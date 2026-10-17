package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.payment.PaymentDetails;
import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        schema = "gym",
        name = "payments")
class PaymentJpaEntity {

    private static final UUID HISTORICAL_BRANCH_ID =
            UUID.fromString("7b0bf7d5-5184-43d2-8f9a-200000000002");

    @Id
    private UUID id;

    /**
     * Generated always as identity — never written by the application.
     * Refreshed via entityManager.refresh() after saveAndFlush.
     */
    @Column(
            name = "payment_number",
            insertable = false,
            updatable = false)
    private Long paymentNumber;

    /**
     * Generated always as stored expression — never written by the application.
     * Refreshed via entityManager.refresh() after saveAndFlush.
     */
    @Column(
            name = "payment_code",
            insertable = false,
            updatable = false,
            length = 32)
    private String paymentCode;

    @Column(
            name = "client_id",
            nullable = false)
    private UUID clientId;

    @Column(
            name = "membership_id")
    private UUID membershipId;

    @Column(
            name = "membership_period_id")
    private UUID membershipPeriodId;

    @Column(
            nullable = false,
            precision = 12,
            scale = 2)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
            nullable = false,
            length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "payment_method",
            nullable = false,
            length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20)
    private PaymentStatus status;

    @Column(
            name = "external_reference",
            length = 128)
    private String externalReference;

    @Column(
            name = "paid_at",
            nullable = false)
    private Instant paidAt;

    @Column(
            name = "registered_by_user_id",
            nullable = false)
    private UUID registeredByUserId;

    @Column(
            name = "registered_at_branch_id",
            nullable = false)
    private UUID registeredAtBranchId;

    @Column(
            name = "created_at",
            nullable = false)
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PaymentJpaEntity() {
    }

    static PaymentJpaEntity register(
            UUID clientId,
            UUID membershipId,
            UUID membershipPeriodId,
            BigDecimal amount,
            String currency,
            PaymentMethod paymentMethod,
            String externalReference,
            Instant paidAt,
            AuthenticatedActor actor,
            Instant occurredAt) {

        return register(clientId, membershipId, membershipPeriodId, amount, currency,
                paymentMethod, externalReference, paidAt, actor, occurredAt, null);
    }

    static PaymentJpaEntity register(
            UUID clientId,
            UUID membershipId,
            UUID membershipPeriodId,
            BigDecimal amount,
            String currency,
            PaymentMethod paymentMethod,
            String externalReference,
            Instant paidAt,
            AuthenticatedActor actor,
            Instant occurredAt,
            UUID registeredAtBranchId) {

        PaymentJpaEntity entity = new PaymentJpaEntity();

        entity.id = UUID.randomUUID();
        entity.clientId = clientId;
        entity.membershipId = membershipId;
        entity.membershipPeriodId = membershipPeriodId;
        entity.amount = amount;
        entity.currency = currency;
        entity.paymentMethod = paymentMethod;
        entity.status = PaymentStatus.PAID;
        entity.externalReference = externalReference;
        entity.paidAt = paidAt;
        entity.registeredByUserId = actor.id();
        entity.registeredAtBranchId = registeredAtBranchId == null
                ? HISTORICAL_BRANCH_ID : registeredAtBranchId;
        entity.createdAt = occurredAt;
        entity.updatedAt = occurredAt;

        return entity;
    }

    static PaymentJpaEntity registerProviderConfirmed(
            UUID paymentId,
            UUID clientId,
            UUID membershipId,
            UUID membershipPeriodId,
            BigDecimal amount,
            String currency,
            UUID registeredByUserId,
            Instant paidAt,
            Instant occurredAt) {

        return registerProviderConfirmed(paymentId, clientId, membershipId,
                membershipPeriodId, amount, currency, registeredByUserId, paidAt,
                occurredAt, null);
    }

    static PaymentJpaEntity registerProviderConfirmed(
            UUID paymentId,
            UUID clientId,
            UUID membershipId,
            UUID membershipPeriodId,
            BigDecimal amount,
            String currency,
            UUID registeredByUserId,
            Instant paidAt,
            Instant occurredAt,
            UUID registeredAtBranchId) {

        PaymentJpaEntity entity = new PaymentJpaEntity();

        entity.id = paymentId;
        entity.clientId = clientId;
        entity.membershipId = membershipId;
        entity.membershipPeriodId = membershipPeriodId;
        entity.amount = amount;
        entity.currency = currency;
        entity.paymentMethod = PaymentMethod.CARD;
        entity.status = PaymentStatus.PAID;
        entity.externalReference = null;
        entity.paidAt = paidAt;
        entity.registeredByUserId = registeredByUserId;
        entity.registeredAtBranchId = registeredAtBranchId == null
                ? HISTORICAL_BRANCH_ID : registeredAtBranchId;
        entity.createdAt = occurredAt;
        entity.updatedAt = occurredAt;

        return entity;
    }

    PaymentDetails toDetails() {
        return new PaymentDetails(
                id,
                paymentCode,
                clientId,
                membershipId,
                membershipPeriodId,
                amount,
                currency,
                paymentMethod,
                status,
                externalReference,
                paidAt,
                registeredByUserId,
                createdAt,
                updatedAt,
                version,
                registeredAtBranchId);
    }

    UUID id() {
        return id;
    }

    String paymentCode() {
        return paymentCode;
    }

    PaymentMethod paymentMethod() {
        return paymentMethod;
    }

    String externalReference() {
        return externalReference;
    }
}
