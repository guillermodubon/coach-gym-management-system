package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentAttemptFailureCode;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptDetails;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptProviderStatusChanged;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatus;
import io.github.guillermodubon.coachgym.payment.PaymentDetails;
import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import io.github.guillermodubon.coachgym.payment.PaymentProviderEventAcknowledged;
import io.github.guillermodubon.coachgym.payment.PaymentProviderPaymentConfirmed;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Applies only verified provider outcomes and keeps event, attempt, and payment writes atomic. */
@Service
public class PaymentProviderEventApplicationService {

    private final PaymentProviderEventStore eventStore;
    private final PaymentAttemptStore paymentAttemptStore;
    private final ProviderConfirmedPaymentStore confirmedPaymentStore;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PaymentProviderEventApplicationService(
            PaymentProviderEventStore eventStore,
            PaymentAttemptStore paymentAttemptStore,
            ProviderConfirmedPaymentStore confirmedPaymentStore,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.eventStore = Objects.requireNonNull(eventStore);
        this.paymentAttemptStore = Objects.requireNonNull(paymentAttemptStore);
        this.confirmedPaymentStore = Objects.requireNonNull(confirmedPaymentStore);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Processes one already verified provider event. A rejected event is durable but has
     * no payment-attempt side effect; a failed transaction leaves the reservation retryable.
     */
    @Transactional
    public PaymentProviderEventProcessingResult process(VerifiedPaymentProviderEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Verified provider event is required.");
        }

        PaymentProviderEventReservation reservation = eventStore.reserve(
                new PersistPaymentProviderEventCommand(
                        UUID.randomUUID(), event.provider(), event.providerEventReference(),
                        event.eventType(), event.paymentAttemptId(), clock.instant()));
        PaymentProviderEventDetails stored = eventStore.findForProcessing(
                event.provider(), event.providerEventReference());

        if (stored.processingResult() != PaymentProviderEventProcessingResult.PENDING) {
            publishDuplicateAcknowledgement(event, stored.processingResult());
            return stored.processingResult();
        }
        if (reservation == PaymentProviderEventReservation.ALREADY_RESERVED
                && !sameIdentity(stored, event)) {
            publishDuplicateAcknowledgement(
                    event, PaymentProviderEventProcessingResult.REJECTED);
            return PaymentProviderEventProcessingResult.REJECTED;
        }
        if (!sameIdentity(stored, event)) {
            eventStore.finalizeProcessing(finalizeCommand(
                    event, PaymentProviderEventProcessingResult.REJECTED));
            return PaymentProviderEventProcessingResult.REJECTED;
        }

        PaymentAttemptProviderDetails providerDetails = paymentAttemptStore
                .findProviderDetails(event.paymentAttemptId())
                .orElseThrow(() -> new IllegalStateException("Payment attempt was not found."));
        if (!matchesLineage(event, providerDetails)) {
            eventStore.finalizeProcessing(finalizeCommand(
                    event, PaymentProviderEventProcessingResult.REJECTED));
            return PaymentProviderEventProcessingResult.REJECTED;
        }

        PaymentAttemptStatus currentStatus = providerDetails.details().status();
        if (currentStatus != PaymentAttemptStatus.PROCESSING) {
            eventStore.finalizeProcessing(finalizeCommand(
                    event, PaymentProviderEventProcessingResult.REJECTED));
            return PaymentProviderEventProcessingResult.REJECTED;
        }

        Instant occurredAt = event.occurredAt();
        if (event.eventType() == PaymentProviderEventType.CHECKOUT_COMPLETED) {
            PaymentDetails payment = confirmedPaymentStore.register(
                    new ProviderConfirmedPaymentCommand(
                            UUID.randomUUID(),
                            providerDetails.details().clientId(),
                            providerDetails.details().membershipId(),
                            providerDetails.details().membershipPeriodId(),
                            event.amount(),
                            event.currency(),
                            providerDetails.details().createdByUserId(),
                            occurredAt,
                            occurredAt));
            PaymentAttemptDetails succeeded = paymentAttemptStore.markProviderSucceeded(
                    new ConfirmProviderPaymentAttemptCommand(
                            providerDetails.details().id(),
                            providerDetails.details().version(),
                            event.providerPaymentReference(),
                            payment.id(),
                            occurredAt));
            eventStore.finalizeProcessing(finalizeCommand(
                    event, PaymentProviderEventProcessingResult.PROCESSED));
            publishAfterCommit(new PaymentProviderPaymentConfirmed(
                    succeeded.id(), payment.id(), event.provider(), payment.amount(),
                    payment.currency(), occurredAt));
            publishAfterCommit(new PaymentAttemptProviderStatusChanged(
                    succeeded.id(), succeeded.provider(), currentStatus, succeeded.status(),
                    null, succeeded.confirmedPaymentId(), true, occurredAt));
            return PaymentProviderEventProcessingResult.PROCESSED;
        }

        ProviderOutcome outcome = outcome(event.eventType());
        PaymentAttemptDetails terminal = paymentAttemptStore.markProviderFailure(
                new ProviderPaymentFailureCommand(
                        providerDetails.details().id(),
                        providerDetails.details().version(),
                        outcome.status(),
                        outcome.failureCode(),
                        occurredAt));
        eventStore.finalizeProcessing(finalizeCommand(
                event, PaymentProviderEventProcessingResult.PROCESSED));
        publishAfterCommit(new PaymentAttemptProviderStatusChanged(
                terminal.id(), terminal.provider(), currentStatus, terminal.status(),
                terminal.failureCode(), null, true, occurredAt));
        return PaymentProviderEventProcessingResult.PROCESSED;
    }

    private static boolean sameIdentity(
            PaymentProviderEventDetails stored,
            VerifiedPaymentProviderEvent event) {
        return stored.provider() == event.provider()
                && Objects.equals(stored.providerEventReference(), event.providerEventReference())
                && stored.eventType() == event.eventType()
                && Objects.equals(stored.paymentAttemptId(), event.paymentAttemptId());
    }

    private static boolean matchesLineage(
            VerifiedPaymentProviderEvent event,
            PaymentAttemptProviderDetails providerDetails) {
        if (providerDetails.details().provider() != event.provider()) {
            return false;
        }
        if (providerDetails.checkoutReference() == null) {
            return false;
        }
        if (event.checkoutReference() != null
                && !Objects.equals(providerDetails.checkoutReference(), event.checkoutReference())) {
            return false;
        }
        if (event.eventType() == PaymentProviderEventType.CHECKOUT_COMPLETED) {
            return providerDetails.details().expectedAmount().compareTo(event.amount()) == 0
                    && Objects.equals(providerDetails.details().currency(), event.currency());
        }
        return true;
    }

    private static ProviderOutcome outcome(PaymentProviderEventType eventType) {
        return switch (eventType) {
            case PAYMENT_FAILED -> new ProviderOutcome(
                    PaymentAttemptStatus.FAILED, PaymentAttemptFailureCode.PROVIDER_DECLINED);
            case CHECKOUT_CANCELLED -> new ProviderOutcome(
                    PaymentAttemptStatus.CANCELLED, PaymentAttemptFailureCode.PROVIDER_CANCELLED);
            case CHECKOUT_EXPIRED -> new ProviderOutcome(
                    PaymentAttemptStatus.EXPIRED, PaymentAttemptFailureCode.PROVIDER_EXPIRED);
            case CHECKOUT_COMPLETED -> throw new IllegalArgumentException(
                    "Completed events do not represent a failure outcome.");
        };
    }

    private FinalizePaymentProviderEventCommand finalizeCommand(
            VerifiedPaymentProviderEvent event,
            PaymentProviderEventProcessingResult result) {
        return new FinalizePaymentProviderEventCommand(
                event.provider(), event.providerEventReference(), result, clock.instant());
    }

    private void publishAfterCommit(Object event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventPublisher.publishEvent(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publishEvent(event);
            }
        });
    }

    private void publishDuplicateAcknowledgement(
            VerifiedPaymentProviderEvent event,
            PaymentProviderEventProcessingResult result) {
        // The duplicate branch has no further state change. Publishing inside
        // the current transaction makes its audit record commit atomically
        // with the acknowledgement and disappear on rollback.
        eventPublisher.publishEvent(new PaymentProviderEventAcknowledged(
                event.paymentAttemptId(), event.provider(),
                event.eventType().name(), result.name(), true, clock.instant()));
    }

    private record ProviderOutcome(
            PaymentAttemptStatus status,
            PaymentAttemptFailureCode failureCode) {
    }
}
