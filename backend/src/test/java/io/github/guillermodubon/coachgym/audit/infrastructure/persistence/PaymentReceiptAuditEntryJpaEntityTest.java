package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.payment.PaymentReceiptGenerated;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PaymentReceiptAuditEntryJpaEntityTest {

    private static final UUID RECEIPT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000801");
    private static final UUID PAYMENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000802");
    private static final UUID ACTOR_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000803");
    private static final Instant GENERATED_AT =
            Instant.parse("2026-09-12T16:00:00Z");

    @Test
    void recordsCanonicalReceiptGenerationWithActorAndResourceSnapshot() {
        AuditEntryJpaEntity entry = AuditEntryJpaEntity.from(event(false));

        assertThat(field(entry, "actionCode"))
                .isEqualTo("PAYMENT_RECEIPT_GENERATED");
        assertThat(field(entry, "resourceType"))
                .isEqualTo("PAYMENT_RECEIPT");
        assertThat(field(entry, "resourceId"))
                .isEqualTo(RECEIPT_ID);
        assertThat(field(entry, "resourceCodeSnapshot"))
                .isEqualTo("REC-000001");
        assertThat(field(entry, "actorUserId"))
                .isEqualTo(ACTOR_ID);
        assertThat(field(entry, "actorIdentifierSnapshot"))
                .isEqualTo("coach-admin");
        assertThat(field(entry, "occurredAt"))
                .isEqualTo(GENERATED_AT);
        assertThat(field(entry, "summary"))
                .isEqualTo("Payment receipt generated.");
    }

    @Test
    void storesOnlyApprovedPrivacySafeReceiptMetadata() {
        Map<String, Object> metadata = metadata(
                AuditEntryJpaEntity.from(event(true)));

        assertThat(metadata).containsExactlyInAnyOrderEntriesOf(Map.of(
                "paymentId", PAYMENT_ID.toString(),
                "paymentCode", "PAY-000001",
                "amount", "25.00",
                "currency", "USD",
                "testMode", true));
        assertThat(metadata.keySet()).isSubsetOf(Set.of(
                "paymentId", "paymentCode", "amount", "currency", "testMode"));
        assertThat(metadata.values())
                .doesNotContain("client@example.com", "+50370000000")
                .doesNotContain("/absolute/path/receipt.pdf", "cardNumber",
                        "sk_test_secret", "pi_test_sensitive");
    }

    @Test
    void keepsReceiptAuditMetadataImmutable() {
        Map<String, Object> metadata = metadata(
                AuditEntryJpaEntity.from(event(false)));

        assertThatThrownBy(() -> metadata.put("injected", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static PaymentReceiptGenerated event(boolean testMode) {
        return new PaymentReceiptGenerated(
                RECEIPT_ID,
                "REC-000001",
                PAYMENT_ID,
                "PAY-000001",
                PaymentStatus.PAID,
                new BigDecimal("25.00"),
                "USD",
                ACTOR_ID,
                "coach-admin",
                testMode,
                GENERATED_AT);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(AuditEntryJpaEntity entry) {
        return (Map<String, Object>) ReflectionTestUtils.getField(entry, "metadata");
    }

    private static Object field(AuditEntryJpaEntity entry, String name) {
        return ReflectionTestUtils.getField(entry, name);
    }
}
