package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import io.github.guillermodubon.coachgym.payment.PaymentCorrectionDetails;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionStateConflictException;
import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionVersionConflictException;
import io.github.guillermodubon.coachgym.payment.application.RefundPaymentCommand;
import io.github.guillermodubon.coachgym.payment.application.VoidPaymentCommand;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaymentCorrectionPersistenceIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Autowired
    private JdbcPaymentCorrectionAdapter adapter;

    @Autowired
    private JdbcPaymentCorrectionQuery correctionQuery;

    @Autowired
    private JdbcPaymentStatusHistoryQuery historyQuery;

    @Test
    void voidsPaymentAndCreatesAppendOnlyHistory() {
        UUID paymentId = insertPaidPayment("CASH");
        AuthenticatedActor actor = new AuthenticatedActor(adminId, "admin");

        PaymentCorrectionDetails result = adapter.voidPayment(
                new VoidPaymentCommand(paymentId, "Registered twice", 0),
                actor,
                occurredAt());

        assertThat(result.currentStatus()).isEqualTo(PaymentStatus.VOIDED);
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.refund()).isNull();
        assertThat(correctionQuery.findByPaymentId(paymentId)).contains(result);
        assertThat(historyQuery.findByPaymentId(paymentId, 0, 25).content())
                .hasSize(1)
                .first()
                .satisfies(history -> {
                    assertThat(history.previousStatus()).isEqualTo(PaymentStatus.PAID);
                    assertThat(history.newStatus()).isEqualTo(PaymentStatus.VOIDED);
                });
        assertThat(refundCount(paymentId)).isZero();
    }

    @Test
    void refundsOriginalAmountCurrencyAndMethod() {
        UUID paymentId = insertPaidPayment("CASH");
        AuthenticatedActor actor = new AuthenticatedActor(adminId, "admin");

        PaymentCorrectionDetails result = adapter.refundPayment(
                new RefundPaymentCommand(
                        paymentId, "Approved full refund", "REF-EXT-1", 0),
                actor,
                occurredAt());

        assertThat(result.currentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(result.refund().amount()).isEqualByComparingTo("25.00");
        assertThat(result.refund().currency()).isEqualTo("USD");
        assertThat(refundCount(paymentId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select refund_method from gym.payment_refunds
                where payment_id = ?
                """, String.class, paymentId)).isEqualTo("CASH");
    }

    @Test
    void distinguishesVersionAndStateConflicts() {
        UUID versionPayment = insertPaidPayment("CASH");
        AuthenticatedActor actor = new AuthenticatedActor(adminId, "admin");

        assertThatThrownBy(() -> adapter.voidPayment(
                new VoidPaymentCommand(versionPayment, "Registered twice", 7),
                actor,
                occurredAt()))
                .isInstanceOf(PaymentCorrectionVersionConflictException.class);

        UUID statePayment = insertPaidPayment("CASH");
        adapter.voidPayment(
                new VoidPaymentCommand(statePayment, "Registered twice", 0),
                actor,
                occurredAt());

        assertThatThrownBy(() -> adapter.refundPayment(
                new RefundPaymentCommand(
                        statePayment, "Approved refund", null, 1),
                actor,
                occurredAt()))
                .isInstanceOf(PaymentCorrectionStateConflictException.class);
    }

    private UUID insertPaidPayment(String method) {
        UUID clientId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.clients
                    (id, first_name, last_name, phone, status,
                     created_by_user_id, updated_by_user_id,
                     created_at, updated_at, version)
                values (?, 'Correction', 'Persistence', '+50370003003',
                        'ACTIVE', ?, ?, ?, ?, 0)
                """,
                clientId, adminId, adminId, offset(), offset());

        UUID paymentId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.payments
                    (id, client_id, amount, currency, payment_method,
                     status, paid_at, registered_by_user_id,
                     created_at, updated_at, version)
                values (?, ?, 25.00, 'USD', ?, 'PAID', ?, ?, ?, ?, 0)
                """,
                paymentId, clientId, method, offset(), adminId, offset(), offset());
        jdbcTemplate.update("""
                insert into gym.payment_status_history
                    (id, payment_id, previous_status, new_status,
                     reason, occurred_at, changed_by_user_id)
                values (?, ?, null, 'PAID', 'Payment registered.', ?, ?)
                """,
                UUID.randomUUID(), paymentId, offset(), adminId);
        return paymentId;
    }

    private int refundCount(UUID paymentId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from gym.payment_refunds where payment_id = ?",
                Integer.class,
                paymentId);
        return count == null ? 0 : count;
    }

    private static Instant occurredAt() {
        return Instant.parse("2026-09-10T15:00:00Z");
    }

    private static OffsetDateTime offset() {
        return OffsetDateTime.ofInstant(occurredAt(), ZoneOffset.UTC);
    }
}
