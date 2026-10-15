package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

class PaymentCorrectionDatabaseConstraintIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Test
    void rejectsUnsupportedPaymentStatusTransition() {
        UUID paymentId = insertPaidPayment();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into gym.payment_status_history
                    (id, payment_id, previous_status, new_status,
                     reason, occurred_at, changed_by_user_id)
                values (?, ?, 'VOIDED', 'REFUNDED', ?, ?, ?)
                """,
                UUID.randomUUID(),
                paymentId,
                "Unsupported transition",
                occurredAt(),
                adminId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsRefundWithDifferentAmountCurrencyOrMethod() {
        UUID paymentId = insertPaidPayment();

        assertThatThrownBy(() -> insertRefund(
                paymentId, new BigDecimal("9.99"), "USD", "CASH"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertRefund(
                paymentId, new BigDecimal("25.00"), "EUR", "CASH"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertRefund(
                paymentId, new BigDecimal("25.00"), "USD", "CARD"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void historyAndRefundRowsCannotBeUpdatedOrDeleted() {
        UUID paymentId = insertPaidPayment();
        UUID historyId = UUID.randomUUID();

        jdbcTemplate.update("""
            insert into gym.payment_status_history
                (id, payment_id, previous_status, new_status,
                 reason, occurred_at, changed_by_user_id)
            values (?, ?, 'PAID', 'VOIDED', ?, ?, ?)
            """,
                historyId,
                paymentId,
                "Registered twice",
                occurredAt(),
                adminId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
            update gym.payment_status_history
            set reason = 'Changed reason'
            where id = ?
            """, historyId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
            delete from gym.payment_status_history
            where id = ?
            """, historyId))
                .isInstanceOf(DataAccessException.class);

        UUID refundId = insertRefund(
                paymentId,
                new BigDecimal("25.00"),
                "USD",
                "CASH");

        assertThatThrownBy(() -> jdbcTemplate.update("""
            update gym.payment_refunds
            set reason = 'Changed reason'
            where id = ?
            """, refundId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
            delete from gym.payment_refunds
            where id = ?
            """, refundId))
                .isInstanceOf(DataAccessException.class);
    }

    private UUID insertPaidPayment() {
        UUID clientId = insertClient();
        UUID paymentId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.payments
                    (id, client_id, amount, currency, payment_method,
                     status, paid_at, registered_by_user_id,
                     created_at, updated_at, version)
                values (?, ?, 25.00, 'USD', 'CASH', 'PAID', ?, ?, ?, ?, 0)
                """,
                paymentId,
                clientId,
                occurredAt(),
                adminId,
                occurredAt(),
                occurredAt());
        return paymentId;
    }

    private UUID insertClient() {
        UUID clientId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.clients
                    (id, first_name, last_name, phone, status,
                     created_by_user_id, updated_by_user_id,
                     created_at, updated_at, version)
                values (?, 'Payment', 'Correction', '+50370002003',
                        'ACTIVE', ?, ?, ?, ?, 0)
                """,
                clientId,
                adminId,
                adminId,
                occurredAt(),
                occurredAt());
        return clientId;
    }

    private UUID insertRefund(
            UUID paymentId,
            BigDecimal amount,
            String currency,
            String method) {
        UUID refundId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.payment_refunds
                    (id, payment_id, amount, currency, refund_method,
                     reason, refunded_at, refunded_by_user_id, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                refundId,
                paymentId,
                amount,
                currency,
                method,
                "Approved full refund",
                occurredAt(),
                adminId,
                occurredAt());
        return refundId;
    }

    private static OffsetDateTime occurredAt() {
        return OffsetDateTime.of(
                2026, 9, 9, 14, 0, 0, 0, ZoneOffset.UTC);
    }
}
