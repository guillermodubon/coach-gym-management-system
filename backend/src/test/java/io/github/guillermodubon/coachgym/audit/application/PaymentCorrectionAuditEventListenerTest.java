package io.github.guillermodubon.coachgym.audit.application;

import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.payment.PaymentRefunded;
import io.github.guillermodubon.coachgym.payment.PaymentVoided;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentCorrectionAuditEventListenerTest {

    @Mock AuditEntryStore store;
    @InjectMocks PaymentAuditEventListener listener;

    @Test
    void forwardsVoidedEvent() {
        PaymentVoided event = new PaymentVoided(
                UUID.randomUUID(), "PAY-000001", PaymentStatus.PAID,
                PaymentStatus.VOIDED, UUID.randomUUID(), "coach-admin",
                Instant.parse("2026-09-10T18:00:00Z"));
        listener.record(event);
        verify(store).recordPaymentVoided(event);
    }

    @Test
    void forwardsRefundedEvent() {
        PaymentRefunded event = new PaymentRefunded(
                UUID.randomUUID(), "PAY-000002", UUID.randomUUID(),
                PaymentStatus.PAID, PaymentStatus.REFUNDED,
                new BigDecimal("25.00"), "USD", true,
                UUID.randomUUID(), "coach-admin",
                Instant.parse("2026-09-10T18:00:00Z"));
        listener.record(event);
        verify(store).recordPaymentRefunded(event);
    }
}
