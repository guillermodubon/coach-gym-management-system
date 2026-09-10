package io.github.guillermodubon.coachgym.payment.application;


import io.github.guillermodubon.coachgym.payment.PaymentCorrectionDetails;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.payment.PaymentStatusHistoryPage;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.guillermodubon.coachgym.payment.PaymentRefunded;
import io.github.guillermodubon.coachgym.payment.PaymentVoided;


@Service
public class PaymentCorrectionApplicationService {

    private final PaymentCorrectionStore correctionStore;
    private final PaymentCorrectionQuery correctionQuery;
    private final PaymentStatusHistoryQuery historyQuery;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PaymentCorrectionApplicationService(
            PaymentCorrectionStore correctionStore,
            PaymentCorrectionQuery correctionQuery,
            PaymentStatusHistoryQuery historyQuery,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.correctionStore = Objects.requireNonNull(correctionStore);
        this.correctionQuery = Objects.requireNonNull(correctionQuery);
        this.historyQuery = Objects.requireNonNull(historyQuery);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentCorrectionDetails voidPayment(
            VoidPaymentCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(command, "Void payment command is required.");
        requireActor(actor);
        Instant occurredAt = clock.instant();

        PaymentCorrectionDetails result = correctionStore.voidPayment(
                command, actor, occurredAt);

        eventPublisher.publishEvent(new PaymentVoided(
                result.paymentId(),
                result.paymentCode(),
                PaymentStatus.PAID,
                PaymentStatus.VOIDED,
                actor.id(),
                actor.username(),
                occurredAt));

        return result;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentCorrectionDetails refundPayment(
            RefundPaymentCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(command, "Refund payment command is required.");
        requireActor(actor);
        Instant occurredAt = clock.instant();

        PaymentCorrectionDetails result = correctionStore.refundPayment(
                command, actor, occurredAt);

        if (result.refund() == null) {
            throw new IllegalStateException(
                    "Refund persistence returned no refund details.");
        }

        eventPublisher.publishEvent(new PaymentRefunded(
                result.paymentId(),
                result.paymentCode(),
                result.refund().refundId(),
                PaymentStatus.PAID,
                PaymentStatus.REFUNDED,
                result.refund().amount(),
                result.refund().currency(),
                result.refund().externalReference() != null,
                actor.id(),
                actor.username(),
                occurredAt));

        return result;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public Optional<PaymentCorrectionDetails> findCorrection(UUID paymentId) {
        requirePaymentId(paymentId);
        return correctionQuery.findByPaymentId(paymentId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PaymentStatusHistoryPage findStatusHistory(
            UUID paymentId,
            int page,
            int size) {
        requirePaymentId(paymentId);
        if (page < 0 || size < 1 || size > 100) {
            throw new PaymentCorrectionValidationException(
                    "Payment history pagination is invalid.");
        }
        return historyQuery.findByPaymentId(paymentId, page, size);
    }

    private static void requirePaymentId(UUID paymentId) {
        if (paymentId == null) {
            throw new PaymentCorrectionValidationException(
                    "Payment id is required.");
        }
    }

    private static void requireActor(AuthenticatedActor actor) {
        if (actor == null || actor.id() == null
                || actor.username() == null || actor.username().isBlank()) {
            throw new PaymentCorrectionValidationException(
                    "Authenticated actor is required.");
        }
    }
}
