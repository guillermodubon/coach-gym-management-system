package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.payment.PaymentRefunded;
import io.github.guillermodubon.coachgym.payment.PaymentVoided;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaymentCorrectionAuditIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Autowired AuditEntryPersistenceAdapter adapter;

    @Test
    void persistsSafeVoidAndRefundAuditMetadata() {
        UUID voidPaymentId = UUID.randomUUID();
        adapter.recordPaymentVoided(new PaymentVoided(
                voidPaymentId, "PAY-VOID", PaymentStatus.PAID,
                PaymentStatus.VOIDED, adminId, "coach-admin", now()));

        UUID refundPaymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        adapter.recordPaymentRefunded(new PaymentRefunded(
                refundPaymentId, "PAY-REFUND", refundId,
                PaymentStatus.PAID, PaymentStatus.REFUNDED,
                new BigDecimal("25.00"), "USD", true,
                adminId, "coach-admin", now()));

        Map<String, Object> voidRow = jdbcTemplate.queryForMap("""
                select action_code, resource_type, resource_code_snapshot,
                       metadata::text as metadata
                from gym.audit_entries where resource_id = ?
                """, voidPaymentId);
        assertThat(voidRow.get("action_code")).isEqualTo("PAYMENT_VOIDED");
        assertThat(voidRow.get("resource_type")).isEqualTo("PAYMENT");
        assertThat(voidRow.get("metadata").toString())
                .contains("previousStatus", "PAID", "newStatus", "VOIDED")
                .doesNotContain("reason", "externalReference", "stripe", "card");

        Map<String, Object> refundRow = jdbcTemplate.queryForMap("""
                select action_code, metadata::text as metadata
                from gym.audit_entries where resource_id = ?
                """, refundPaymentId);
        assertThat(refundRow.get("action_code")).isEqualTo("PAYMENT_REFUNDED");
        assertThat(refundRow.get("metadata").toString())
                .contains(refundId.toString(), "25.00", "USD",
                        "externalReferencePresent")
                .doesNotContain("REF-SENSITIVE", "reason", "paymentIntent");
    }

    private static Instant now() {
        return Instant.parse("2026-09-10T18:00:00Z");
    }
}
