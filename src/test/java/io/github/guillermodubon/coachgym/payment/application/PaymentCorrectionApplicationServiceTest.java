package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentCorrectionDetails;
import io.github.guillermodubon.coachgym.payment.PaymentRefundDetails;
import io.github.guillermodubon.coachgym.payment.PaymentRefunded;
import io.github.guillermodubon.coachgym.payment.PaymentVoided;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;


@ExtendWith(MockitoExtension.class)
class PaymentCorrectionApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T18:00:00Z");

    @Mock private PaymentCorrectionStore store;
    @Mock private PaymentCorrectionQuery query;
    @Mock private PaymentStatusHistoryQuery historyQuery;
    @Mock private ApplicationEventPublisher publisher;

    private PaymentCorrectionApplicationService service;
    private AuthenticatedActor actor;

    @BeforeEach
    void setUp() {
        service = new PaymentCorrectionApplicationService(
                store, query, historyQuery, publisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        actor = new AuthenticatedActor(UUID.randomUUID(), "admin");
    }

    @Test
    void voidsPaymentUsingServerClockAndPublishesEvent() {
        UUID paymentId = UUID.randomUUID();
        VoidPaymentCommand command = new VoidPaymentCommand(
                paymentId, "Registered twice", 0);
        PaymentCorrectionDetails result = PaymentCorrectionDetails.voided(
                paymentId, "PAY-000001", command.reason(), NOW,
                actor.id(), 1);
        when(store.voidPayment(command, actor, NOW)).thenReturn(result);

        assertThat(service.voidPayment(command, actor)).isSameAs(result);

        ArgumentCaptor<PaymentVoided> captor =
                ArgumentCaptor.forClass(PaymentVoided.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().paymentId()).isEqualTo(paymentId);
        assertThat(captor.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void refundsPaymentAndPublishesSafeRefundEvent() {
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        RefundPaymentCommand command = new RefundPaymentCommand(
                paymentId, "Approved full refund", "REF-001", 0);
        PaymentRefundDetails refund = new PaymentRefundDetails(
                refundId, paymentId, new BigDecimal("25.00"), "USD",
                command.reason(), command.externalReference(), NOW, actor.id());
        PaymentCorrectionDetails result = PaymentCorrectionDetails.refunded(
                paymentId, "PAY-000002", command.reason(), NOW,
                actor.id(), 1, refund);
        when(store.refundPayment(command, actor, NOW)).thenReturn(result);

        assertThat(service.refundPayment(command, actor)).isSameAs(result);

        ArgumentCaptor<PaymentRefunded> captor =
                ArgumentCaptor.forClass(PaymentRefunded.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().refundId()).isEqualTo(refundId);
        assertThat(captor.getValue().externalReferencePresent()).isTrue();
        assertThat(captor.getValue().amount()).isEqualByComparingTo("25.00");
    }
}
