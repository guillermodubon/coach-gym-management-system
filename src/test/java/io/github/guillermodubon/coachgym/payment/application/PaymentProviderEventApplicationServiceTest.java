package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.payment.PaymentAttemptDetails;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptFailureCode;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptProviderStatusChanged;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatus;
import io.github.guillermodubon.coachgym.payment.PaymentDetails;
import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import io.github.guillermodubon.coachgym.payment.PaymentProviderEventAcknowledged;
import io.github.guillermodubon.coachgym.payment.PaymentProviderPaymentConfirmed;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class PaymentProviderEventApplicationServiceTest {

    private static final UUID ATTEMPT_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000702");
    private static final UUID CLIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000703");
    private static final UUID MEMBERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000704");
    private static final UUID PERIOD_ID = UUID.fromString("00000000-0000-0000-0000-000000000705");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000706");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000707");
    private static final Instant NOW = Instant.parse("2026-09-10T16:00:00Z");

    private PaymentProviderEventStore eventStore;
    private PaymentAttemptStore attemptStore;
    private ProviderConfirmedPaymentStore paymentStore;
    private ApplicationEventPublisher eventPublisher;
    private PaymentProviderEventApplicationService service;

    @BeforeEach
    void setUp() {
        eventStore = mock(PaymentProviderEventStore.class);
        attemptStore = mock(PaymentAttemptStore.class);
        paymentStore = mock(ProviderConfirmedPaymentStore.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new PaymentProviderEventApplicationService(
                eventStore, attemptStore, paymentStore, eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsOneCardPaymentForMatchingCompletedEvent() {
        VerifiedPaymentProviderEvent event = completedEvent("evt_success");
        PaymentProviderEventDetails stored = pending(event);
        PaymentAttemptDetails processing = attempt(PaymentAttemptStatus.PROCESSING, 1L, null, null);
        PaymentAttemptDetails succeeded = attempt(PaymentAttemptStatus.SUCCEEDED, 2L,
                null, PAYMENT_ID);
        given(eventStore.reserve(any())).willReturn(PaymentProviderEventReservation.RESERVED);
        given(eventStore.findForProcessing(event.provider(), event.providerEventReference()))
                .willReturn(stored);
        given(attemptStore.findProviderDetails(ATTEMPT_ID))
                .willReturn(java.util.Optional.of(new PaymentAttemptProviderDetails(
                        processing, "cs_test_701")));
        given(paymentStore.register(any())).willReturn(paymentDetails());
        given(attemptStore.markProviderSucceeded(any())).willReturn(succeeded);

        assertThat(service.process(event))
                .isEqualTo(PaymentProviderEventProcessingResult.PROCESSED);

        verify(paymentStore).register(any(ProviderConfirmedPaymentCommand.class));
        verify(attemptStore).markProviderSucceeded(any(ConfirmProviderPaymentAttemptCommand.class));
        verify(eventStore).finalizeProcessing(any(FinalizePaymentProviderEventCommand.class));
        verify(eventPublisher).publishEvent(any(PaymentProviderPaymentConfirmed.class));
        verify(eventPublisher).publishEvent(any(PaymentAttemptProviderStatusChanged.class));
    }

    @Test
    void duplicateProcessedEventDoesNotChangePaymentStateAndAuditsPersistedBranch() {
        VerifiedPaymentProviderEvent event = completedEvent("evt_duplicate");
        given(eventStore.reserve(any())).willReturn(PaymentProviderEventReservation.ALREADY_RESERVED);
        given(eventStore.findForProcessing(event.provider(), event.providerEventReference()))
                .willReturn(new PaymentProviderEventDetails(
                        UUID.randomUUID(), PaymentProvider.STRIPE, event.providerEventReference(),
                        event.eventType(), ATTEMPT_ID,
                        PaymentProviderEventProcessingResult.PROCESSED, NOW, NOW));
        given(attemptStore.findProviderDetails(ATTEMPT_ID))
                .willReturn(java.util.Optional.of(new PaymentAttemptProviderDetails(
                        attempt(PaymentAttemptStatus.SUCCEEDED, 2L, null, PAYMENT_ID),
                        "cs_test_701")));

        assertThat(service.process(event))
                .isEqualTo(PaymentProviderEventProcessingResult.PROCESSED);

        verify(attemptStore).findProviderDetails(ATTEMPT_ID);
        verify(attemptStore, never()).markProviderSucceeded(any());
        verify(attemptStore, never()).markProviderFailure(any());
        verify(paymentStore, never()).register(any());
        verify(eventStore, never()).finalizeProcessing(any());
        ArgumentCaptor<PaymentProviderEventAcknowledged> acknowledgement =
                ArgumentCaptor.forClass(PaymentProviderEventAcknowledged.class);
        verify(eventPublisher).publishEvent(acknowledgement.capture());
        assertThat(acknowledgement.getValue().branchId()).isEqualTo(BRANCH_ID);
    }

    @Test
    void amountMismatchIsRejectedWithoutPaymentOrAttemptTransition() {
        VerifiedPaymentProviderEvent event = completedEvent("evt_mismatch");
        event = new VerifiedPaymentProviderEvent(
                event.provider(), event.providerEventReference(), event.eventType(),
                event.paymentAttemptId(), event.checkoutReference(), event.providerPaymentReference(),
                new BigDecimal("99.00"), event.currency(), event.occurredAt());
        given(eventStore.reserve(any())).willReturn(PaymentProviderEventReservation.RESERVED);
        given(eventStore.findForProcessing(event.provider(), event.providerEventReference()))
                .willReturn(pending(event));
        given(attemptStore.findProviderDetails(ATTEMPT_ID))
                .willReturn(java.util.Optional.of(new PaymentAttemptProviderDetails(
                        attempt(PaymentAttemptStatus.PROCESSING, 1L, null, null),
                        "cs_test_701")));

        assertThat(service.process(event))
                .isEqualTo(PaymentProviderEventProcessingResult.REJECTED);

        verify(paymentStore, never()).register(any());
        verify(attemptStore, never()).markProviderSucceeded(any());
        verify(attemptStore, never()).markProviderFailure(any());
        verify(eventStore).finalizeProcessing(any(FinalizePaymentProviderEventCommand.class));
    }

    @Test
    void providerFailureClosesAttemptWithoutPayment() {
        VerifiedPaymentProviderEvent event = new VerifiedPaymentProviderEvent(
                PaymentProvider.STRIPE, "evt_failed", PaymentProviderEventType.PAYMENT_FAILED,
                ATTEMPT_ID, "cs_test_701", null, null, null, NOW);
        PaymentAttemptDetails processing = attempt(PaymentAttemptStatus.PROCESSING, 1L, null, null);
        PaymentAttemptDetails failed = attempt(PaymentAttemptStatus.FAILED, 2L,
                PaymentAttemptFailureCode.PROVIDER_DECLINED, null);
        given(eventStore.reserve(any())).willReturn(PaymentProviderEventReservation.RESERVED);
        given(eventStore.findForProcessing(event.provider(), event.providerEventReference()))
                .willReturn(pending(event));
        given(attemptStore.findProviderDetails(ATTEMPT_ID))
                .willReturn(java.util.Optional.of(new PaymentAttemptProviderDetails(
                        processing, "cs_test_701")));
        given(attemptStore.markProviderFailure(any())).willReturn(failed);

        assertThat(service.process(event))
                .isEqualTo(PaymentProviderEventProcessingResult.PROCESSED);

        verify(attemptStore).markProviderFailure(any(ProviderPaymentFailureCommand.class));
        verify(paymentStore, never()).register(any());
    }

    private static VerifiedPaymentProviderEvent completedEvent(String reference) {
        return new VerifiedPaymentProviderEvent(
                PaymentProvider.STRIPE, reference, PaymentProviderEventType.CHECKOUT_COMPLETED,
                ATTEMPT_ID, "cs_test_701", "pi_test_701", new BigDecimal("25.00"), "USD", NOW);
    }

    private static PaymentProviderEventDetails pending(VerifiedPaymentProviderEvent event) {
        return new PaymentProviderEventDetails(
                UUID.randomUUID(), event.provider(), event.providerEventReference(),
                event.eventType(), event.paymentAttemptId(),
                PaymentProviderEventProcessingResult.PENDING, NOW, null);
    }

    private static PaymentAttemptDetails attempt(
            PaymentAttemptStatus status,
            long version,
            PaymentAttemptFailureCode failureCode,
            UUID confirmedPaymentId) {
        return new PaymentAttemptDetails(
                ATTEMPT_ID, CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID, PaymentProvider.STRIPE,
                status, new BigDecimal("25.00"), "USD", failureCode, confirmedPaymentId,
                ACTOR_ID, NOW, NOW, status.isTerminal() ? NOW : null, version, BRANCH_ID);
    }

    private static PaymentDetails paymentDetails() {
        return new PaymentDetails(
                PAYMENT_ID, "PAY-000701", CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                new BigDecimal("25.00"), "USD", PaymentMethod.CARD, PaymentStatus.PAID,
                null, NOW, ACTOR_ID, NOW, NOW, 0L);
    }
}
