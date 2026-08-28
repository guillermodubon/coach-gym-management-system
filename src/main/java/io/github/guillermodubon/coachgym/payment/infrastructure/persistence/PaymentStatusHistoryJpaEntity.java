package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        schema = "gym",
        name = "payment_status_history")
class PaymentStatusHistoryJpaEntity {

    @Id
    private UUID id;

    @Column(
            name = "payment_id",
            nullable = false)
    private UUID paymentId;

    /**
     * Nullable — null for the initial PAID registration (null → PAID transition).
     * Schema constraint allows NULL for previous_status.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "previous_status",
            length = 20)
    private PaymentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "new_status",
            nullable = false,
            length = 20)
    private PaymentStatus newStatus;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(
            name = "occurred_at",
            nullable = false)
    private Instant occurredAt;

    @Column(name = "changed_by_user_id")
    private UUID changedByUserId;

    protected PaymentStatusHistoryJpaEntity() {
    }

    static PaymentStatusHistoryJpaEntity initialRegistration(
            UUID paymentId,
            AuthenticatedActor actor,
            Instant occurredAt) {

        PaymentStatusHistoryJpaEntity history =
                new PaymentStatusHistoryJpaEntity();

        history.id = UUID.randomUUID();
        history.paymentId = paymentId;
        history.previousStatus = null;
        history.newStatus = PaymentStatus.PAID;
        history.reason = "Payment registered.";
        history.occurredAt = occurredAt;
        history.changedByUserId = actor.id();

        return history;
    }

    UUID paymentId() {
        return paymentId;
    }

    PaymentStatus previousStatus() {
        return previousStatus;
    }

    PaymentStatus newStatus() {
        return newStatus;
    }

    String reason() {
        return reason;
    }

    UUID changedByUserId() {
        return changedByUserId;
    }
}
