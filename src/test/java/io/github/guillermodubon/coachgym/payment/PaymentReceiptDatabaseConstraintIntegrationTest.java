package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

class PaymentReceiptDatabaseConstraintIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Test
    void finalSchemaContainsReceiptConstraintsAndTriggers() {
        List<String> constraints = jdbcTemplate.queryForList("""
                select constraint_name
                from information_schema.table_constraints
                where table_schema = 'gym'
                  and table_name = 'payment_receipts'
                """, String.class);

        assertThat(constraints)
                .contains(
                        "uq_payment_receipts_receipt_number",
                        "uq_payment_receipts_payment_id",
                        "uq_payment_receipts_storage_key",
                        "ck_payment_receipts_payment_status_snapshot",
                        "ck_payment_receipts_amount_positive",
                        "ck_payment_receipts_amount_calculation",
                        "ck_payment_receipts_content_type",
                        "ck_payment_receipts_checksum_sha256",
                        "fk_payment_receipts_payment",
                        "fk_payment_receipts_generated_by_user");

        List<String> triggers = jdbcTemplate.queryForList("""
                select trigger_name
                from information_schema.triggers
                where trigger_schema = 'gym'
                  and event_object_table = 'payment_receipts'
                """, String.class);

        assertThat(triggers)
                .contains(
                        "trg_payment_receipts_validate",
                        "trg_payment_receipts_immutable");
    }

    @Test
    void canonicalReceiptIsUniqueByPaymentAndReceiptNumber() {
        ReceiptFixture first = insertReceipt(
                insertPaidPayment(), "REC-UNIQUE-001", "receipts/unique-001.pdf");

        assertThatThrownBy(() -> insertReceipt(
                first.paymentId(), "REC-UNIQUE-002", "receipts/unique-002.pdf"))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID secondPaymentId = insertPaidPayment();
        assertThatThrownBy(() -> insertReceipt(
                secondPaymentId, first.receiptNumber(), "receipts/unique-003.pdf"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void receiptRejectsNonPaidPaymentsAndMismatchedFinancialSnapshots() {
        UUID voidedPaymentId = insertPaidPayment(
                "VOIDED", "CASH", "USD", new BigDecimal("25.00"));

        assertThatThrownBy(() -> insertReceipt(
                voidedPaymentId, "REC-VOIDED-001", "receipts/voided-001.pdf"))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID paymentId = insertPaidPayment();
        ReceiptValues values = receiptValues(paymentId, "REC-MISMATCH-001", "receipts/mismatch-001.pdf");

        assertThatThrownBy(() -> insertReceipt(values.withAmount("24.00")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertReceipt(values.withCurrency("EUR")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertReceipt(values.withMethod("CARD")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void receiptRejectsInvalidDocumentMetadata() {
        UUID paymentId = insertPaidPayment();
        ReceiptValues values = receiptValues(
                paymentId, "REC-DOCUMENT-001", "receipts/document-001.pdf");

        assertThatThrownBy(() -> insertReceipt(values.withContentType("text/plain")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertReceipt(values.withSizeBytes(0L)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertReceipt(values.withChecksum("g".repeat(64))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void receiptRowsAreImmutableAndFinancialForeignKeysAreRestrictive() {
        ReceiptFixture receipt = insertReceipt(
                insertPaidPayment(), "REC-IMMUTABLE-001", "receipts/immutable-001.pdf");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                update gym.payment_receipts
                set amount = 24.00
                where id = ?
                """, receipt.id()))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                delete from gym.payment_receipts
                where id = ?
                """, receipt.id()))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from gym.payments where id = ?", receipt.paymentId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void receiptForeignKeysAndSnapshotColumnsHaveExpectedRules() {
        Map<String, Object> paymentColumn = jdbcTemplate.queryForMap("""
                select is_nullable, data_type
                from information_schema.columns
                where table_schema = 'gym'
                  and table_name = 'payment_receipts'
                  and column_name = 'payment_id'
                """);

        assertThat(paymentColumn)
                .containsEntry("is_nullable", "NO")
                .containsEntry("data_type", "uuid");

        Integer restrictiveForeignKeys = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.referential_constraints
                where constraint_schema = 'gym'
                  and constraint_name in (
                      'fk_payment_receipts_payment',
                      'fk_payment_receipts_generated_by_user')
                  and delete_rule = 'RESTRICT'
                """, Integer.class);

        assertThat(restrictiveForeignKeys).isEqualTo(2);
    }

    private ReceiptFixture insertReceipt(
            UUID paymentId,
            String receiptNumber,
            String storageKey) {
        return insertReceipt(receiptValues(paymentId, receiptNumber, storageKey));
    }

    private ReceiptFixture insertReceipt(ReceiptValues values) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.payment_receipts
                    (id, receipt_number, payment_id, payment_code_snapshot,
                     payment_status_snapshot, client_code_snapshot,
                     client_display_name_snapshot, membership_code_snapshot,
                     plan_name_snapshot, promotion_name_snapshot,
                     membership_period_number, period_starts_on, period_ends_on,
                     list_price, discount_amount, amount, currency,
                     payment_method, paid_at, generated_at, generated_by_user_id,
                     generated_by_display_name, test_mode, storage_key,
                     content_type, size_bytes, checksum_sha256, renderer_version,
                     version)
                values (?, ?, ?, ?, 'PAID', ?, 'Constraint Client',
                        'MEM-TEST-0001', 'Premium', null, 1, ?, ?,
                        30.00, 5.00, ?, ?, ?, ?, ?, ?,
                        'Incident Staff', false, ?, ?, ?, ?, 'v1', 0)
                """,
                id,
                values.receiptNumber(),
                values.paymentId(),
                values.paymentCode(),
                values.clientCode(),
                LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 10, 10),
                values.amount(),
                values.currency(),
                values.paymentMethod(),
                values.paidAt(),
                values.generatedAt(),
                adminId,
                values.storageKey(),
                values.contentType(),
                values.sizeBytes(),
                values.checksum());
        return new ReceiptFixture(id, values.paymentId(), values.receiptNumber());
    }

    private ReceiptValues receiptValues(
            UUID paymentId,
            String receiptNumber,
            String storageKey) {
        Map<String, Object> payment = jdbcTemplate.queryForMap("""
                select payment_code, client_id
                from gym.payments
                where id = ?
                """, paymentId);
        UUID clientId = (UUID) payment.get("client_id");
        String clientCode = jdbcTemplate.queryForObject(
                "select client_code from gym.clients where id = ?",
                String.class,
                clientId);
        OffsetDateTime paidAt = jdbcTemplate.queryForObject("""
                select paid_at
                from gym.payments
                where id = ?
                """, (resultSet, rowNumber) -> resultSet.getObject(
                "paid_at", OffsetDateTime.class), paymentId);
        return new ReceiptValues(
                paymentId,
                (String) payment.get("payment_code"),
                clientCode,
                receiptNumber,
                storageKey,
                new BigDecimal("25.00"),
                "USD",
                "CASH",
                paidAt,
                paidAt.plusMinutes(5),
                "application/pdf",
                8L,
                "a".repeat(64));
    }

    private UUID insertPaidPayment() {
        return insertPaidPayment(
                "PAID", "CASH", "USD", new BigDecimal("25.00"));
    }

    private UUID insertPaidPayment(
            String status,
            String method,
            String currency,
            BigDecimal amount) {
        UUID clientId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.clients
                    (id, first_name, last_name, phone, status,
                     created_by_user_id, updated_by_user_id,
                     created_at, updated_at, version)
                values (?, 'Constraint', 'Client', '+50370002004',
                        'ACTIVE', ?, ?, ?, ?, 0)
                """,
                clientId,
                adminId,
                adminId,
                occurredAt(),
                occurredAt());

        UUID paymentId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.payments
                    (id, client_id, amount, currency, payment_method,
                     status, paid_at, registered_by_user_id,
                     created_at, updated_at, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                paymentId,
                clientId,
                amount,
                currency,
                method,
                status,
                occurredAt(),
                adminId,
                occurredAt(),
                occurredAt());
        return paymentId;
    }

    private static OffsetDateTime occurredAt() {
        return OffsetDateTime.of(
                2026, 9, 11, 14, 0, 0, 0, ZoneOffset.UTC);
    }

    private record ReceiptFixture(UUID id, UUID paymentId, String receiptNumber) {
    }

    private record ReceiptValues(
            UUID paymentId,
            String paymentCode,
            String clientCode,
            String receiptNumber,
            String storageKey,
            BigDecimal amount,
            String currency,
            String paymentMethod,
            OffsetDateTime paidAt,
            OffsetDateTime generatedAt,
            String contentType,
            long sizeBytes,
            String checksum) {

        private ReceiptValues withAmount(String value) {
            return new ReceiptValues(
                    paymentId, paymentCode, clientCode, receiptNumber, storageKey,
                    new BigDecimal(value), currency, paymentMethod, paidAt, generatedAt,
                    contentType, sizeBytes, checksum);
        }

        private ReceiptValues withCurrency(String value) {
            return new ReceiptValues(
                    paymentId, paymentCode, clientCode, receiptNumber, storageKey,
                    amount, value, paymentMethod, paidAt, generatedAt,
                    contentType, sizeBytes, checksum);
        }

        private ReceiptValues withMethod(String value) {
            return new ReceiptValues(
                    paymentId, paymentCode, clientCode, receiptNumber, storageKey,
                    amount, currency, value, paidAt, generatedAt,
                    contentType, sizeBytes, checksum);
        }

        private ReceiptValues withContentType(String value) {
            return new ReceiptValues(
                    paymentId, paymentCode, clientCode, receiptNumber, storageKey,
                    amount, currency, paymentMethod, paidAt, generatedAt,
                    value, sizeBytes, checksum);
        }

        private ReceiptValues withSizeBytes(long value) {
            return new ReceiptValues(
                    paymentId, paymentCode, clientCode, receiptNumber, storageKey,
                    amount, currency, paymentMethod, paidAt, generatedAt,
                    contentType, value, checksum);
        }

        private ReceiptValues withChecksum(String value) {
            return new ReceiptValues(
                    paymentId, paymentCode, clientCode, receiptNumber, storageKey,
                    amount, currency, paymentMethod, paidAt, generatedAt,
                    contentType, sizeBytes, value);
        }
    }
}
