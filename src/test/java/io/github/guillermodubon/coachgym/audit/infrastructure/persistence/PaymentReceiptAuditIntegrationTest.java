package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptGenerated;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

class PaymentReceiptAuditIntegrationTest extends AbstractIncidentApiIntegrationTest {

    private static final UUID RECEIPT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000901");
    private static final UUID PAYMENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000902");
    private static final Instant GENERATED_AT =
            Instant.parse("2026-09-12T16:30:00Z");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Test
    void persistsReceiptGenerationAuditWithJsonbAndUtcTimestamp() {
        eventPublisher.publishEvent(new PaymentReceiptGenerated(
                RECEIPT_ID,
                "REC-000901",
                PAYMENT_ID,
                "PAY-000902",
                PaymentStatus.PAID,
                new BigDecimal("25.00"),
                "USD",
                adminId,
                "incident-admin",
                true,
                GENERATED_AT));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                select action_code, resource_type, resource_id,
                       resource_code_snapshot, actor_user_id,
                       actor_identifier_snapshot, occurred_at,
                       metadata::text as metadata,
                       metadata ->> 'paymentId' as payment_id,
                       metadata ->> 'paymentCode' as payment_code,
                       metadata ->> 'amount' as amount,
                       metadata ->> 'currency' as currency,
                       metadata ->> 'testMode' as test_mode
                from gym.audit_entries
                where resource_id = ?
                """, RECEIPT_ID);

        assertThat(row.get("action_code"))
                .isEqualTo("PAYMENT_RECEIPT_GENERATED");
        assertThat(row.get("resource_type"))
                .isEqualTo("PAYMENT_RECEIPT");
        assertThat(row.get("resource_id"))
                .isEqualTo(RECEIPT_ID);
        assertThat(row.get("resource_code_snapshot"))
                .isEqualTo("REC-000901");
        assertThat(row.get("actor_user_id"))
                .isEqualTo(adminId);
        assertThat(row.get("actor_identifier_snapshot"))
                .isEqualTo("incident-admin");
        Object occurredAt = row.get("occurred_at");
        Instant persistedOccurredAt = occurredAt instanceof OffsetDateTime offsetDateTime
                ? offsetDateTime.toInstant()
                : ((java.sql.Timestamp) occurredAt).toInstant();
        assertThat(persistedOccurredAt)
                .isEqualTo(GENERATED_AT);
        assertThat(row)
                .containsEntry("payment_id", PAYMENT_ID.toString())
                .containsEntry("payment_code", "PAY-000902")
                .containsEntry("amount", "25.00")
                .containsEntry("currency", "USD")
                .containsEntry("test_mode", "true");
        assertThat(row.get("metadata").toString())
                .doesNotContain("client@example.com", "+50370000000",
                        "/absolute/path/receipt.pdf", "cardNumber",
                        "sk_test_secret", "pi_test_sensitive");
    }
}
