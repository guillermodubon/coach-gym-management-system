package io.github.guillermodubon.coachgym.audit.application;

import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentRegistered;
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
