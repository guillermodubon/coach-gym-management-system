package io.github.guillermodubon.coachgym.audit.application;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptCreated;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptProviderStatusChanged;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatus;
import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import io.github.guillermodubon.coachgym.payment.PaymentProviderEventAcknowledged;
import io.github.guillermodubon.coachgym.payment.PaymentProviderPaymentConfirmed;
import io.github.guillermodubon.coachgym.payment.PaymentRegistered;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptGenerated;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentAuditEventListenerTest {

    private static final UUID PAYMENT_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");

    private static final UUID CLIENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    private static final UUID ACTOR_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");

    private static final UUID RECEIPT_ID =
            UUID.fromString("60000000-0000-0000-0000-000000000001");

    private static final Instant NOW =
            Instant.parse("2026-08-25T18:35:00Z");

    @Mock
    private AuditEntryStore auditEntryStore;

    private PaymentAuditEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new PaymentAuditEventListener(auditEntryStore);
    }

    @Test
    void forwardsPaymentRegisteredEventToStore() {
        PaymentRegistered event = paymentRegistered(false);

        listener.record(event);

        verify(auditEntryStore).recordPaymentRegistered(event);
    }

    @Test
    void forwardsPaymentRegisteredWithExternalReferenceToStore() {
        PaymentRegistered event = paymentRegistered(true);

        listener.record(event);

        verify(auditEntryStore).recordPaymentRegistered(event);
    }

    @Test
    void forwardsPaymentAttemptLifecycleEventsToStore() {
        PaymentAttemptCreated created = new PaymentAttemptCreated(
                PAYMENT_ID, CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                PaymentProvider.STRIPE, new BigDecimal("25.00"), "USD",
                ACTOR_ID, "coach-admin", NOW);
        PaymentAttemptProviderStatusChanged outcome =
                new PaymentAttemptProviderStatusChanged(
                        PAYMENT_ID, PaymentProvider.STRIPE,
                        PaymentAttemptStatus.PROCESSING,
                        PaymentAttemptStatus.FAILED,
                        io.github.guillermodubon.coachgym.payment.PaymentAttemptFailureCode.PROVIDER_DECLINED,
                        null, true, NOW);
        PaymentProviderEventAcknowledged duplicate = new PaymentProviderEventAcknowledged(
                PAYMENT_ID, PaymentProvider.STRIPE, "CHECKOUT_COMPLETED",
                "PROCESSED", true, NOW);
        PaymentProviderPaymentConfirmed confirmed = new PaymentProviderPaymentConfirmed(
                PAYMENT_ID, CLIENT_ID, PaymentProvider.STRIPE,
                new BigDecimal("25.00"), "USD", NOW);

        listener.record(created);
        listener.record(outcome);
        listener.record(duplicate);
        listener.record(confirmed);

        verify(auditEntryStore).recordPaymentAttemptCreated(created);
        verify(auditEntryStore).recordPaymentAttemptProviderStatusChanged(outcome);
        verify(auditEntryStore).recordPaymentProviderEventAcknowledged(duplicate);
        verify(auditEntryStore).recordPaymentProviderPaymentConfirmed(confirmed);
    }

    @Test
    void forwardsPaymentReceiptGeneratedEventToStoreOnce() {
        PaymentReceiptGenerated event = new PaymentReceiptGenerated(
                RECEIPT_ID,
                "REC-000001",
                PAYMENT_ID,
                "PAY-000001",
                PaymentStatus.PAID,
                new BigDecimal("25.00"),
                "USD",
                ACTOR_ID,
                "coach-admin",
                true,
                NOW);

        listener.record(event);

        verify(auditEntryStore, times(1)).recordPaymentReceiptGenerated(event);
    }

    private static PaymentRegistered paymentRegistered(
            boolean hasExternalReference) {

        return new PaymentRegistered(
                PAYMENT_ID,
                "PAY-000001",
                CLIENT_ID,
                MEMBERSHIP_ID,
                PERIOD_ID,
                new BigDecimal("25.00"),
                "USD",
                PaymentMethod.CASH,
                hasExternalReference,
                Instant.parse("2026-08-25T18:30:00Z"),
                PaymentStatus.PAID,
                ACTOR_ID,
                "coach-admin",
                NOW);
    }
}
