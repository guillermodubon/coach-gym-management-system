package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentRegistered;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PaymentAuditEntryJpaEntityTest {

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

    private static final Instant PAID_AT =
            Instant.parse("2026-08-25T18:30:00Z");

    private static final Instant NOW =
            Instant.parse("2026-08-25T18:35:00Z");

    // ------------------------------------------------------------------
    // Structural fields
    // ------------------------------------------------------------------

    @Test
    void setsCorrectActionCodeAndResourceType() {
        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(event(false));

        assertThat(field(entry, "actionCode"))
                .isEqualTo("PAYMENT_REGISTERED");

        assertThat(field(entry, "resourceType"))
                .isEqualTo("PAYMENT");

        assertThat(field(entry, "resourceId"))
                .isEqualTo(PAYMENT_ID);

        assertThat(field(entry, "resourceCodeSnapshot"))
                .isEqualTo("PAY-000001");

        assertThat(field(entry, "summary"))
                .isEqualTo("Payment registered.");
    }

    @Test
    void setsActorFields() {
        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(event(false));

        assertThat(field(entry, "actorUserId"))
                .isEqualTo(ACTOR_ID);

        assertThat(field(entry, "actorIdentifierSnapshot"))
                .isEqualTo("coach-admin");
    }

    @Test
    void setsOccurredAt() {
        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(event(false));

        assertThat(field(entry, "occurredAt"))
                .isEqualTo(NOW);
    }

    @Test
    void assignsNonNullId() {
        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(event(false));

        assertThat(field(entry, "id")).isNotNull();
    }

    // ------------------------------------------------------------------
    // Metadata — approved keys present
    // ------------------------------------------------------------------

    @Test
    void metadataContainsAllApprovedKeys() {
        Map<String, Object> metadata = metadata(AuditEntryJpaEntity.from(event(false)));

        assertThat(metadata)
                .containsKey("clientId")
                .containsKey("membershipId")
                .containsKey("membershipPeriodId")
                .containsKey("amount")
                .containsKey("currency")
                .containsKey("paymentMethod")
                .containsKey("paidAt")
                .containsKey("resultingStatus")
                .containsKey("hasExternalReference");
    }

    @Test
    void metadataValuesAreCorrect() {
        Map<String, Object> metadata = metadata(AuditEntryJpaEntity.from(event(false)));

        assertThat(metadata)
                .containsEntry("clientId", CLIENT_ID.toString())
                .containsEntry("membershipId", MEMBERSHIP_ID.toString())
                .containsEntry("membershipPeriodId", PERIOD_ID.toString())
                .containsEntry("amount", "25.00")
                .containsEntry("currency", "USD")
                .containsEntry("paymentMethod", "CASH")
                .containsEntry("paidAt", PAID_AT.toString())
                .containsEntry("resultingStatus", "PAID")
                .containsEntry("hasExternalReference", false);
    }

    @Test
    void hasExternalReferenceTrueWhenReferenceWasPresent() {
        Map<String, Object> metadata =
                metadata(AuditEntryJpaEntity.from(event(true)));

        assertThat(metadata)
                .containsEntry("hasExternalReference", true);
    }

    // ------------------------------------------------------------------
    // Security — sensitive reference value must never appear in metadata
    // ------------------------------------------------------------------

    @Test
    void metadataDoesNotContainExternalReferenceValue() {
        // Ensures sensitive reference data (card numbers, tokens, etc.)
        // is never stored in the audit record.
        Map<String, Object> metadata =
                metadata(AuditEntryJpaEntity.from(event(true)));

        assertThat(metadata).doesNotContainKey("externalReference");
        assertThat(metadata.values())
                .doesNotContain("REF-SENSITIVE");
    }

    @Test
    void metadataIsImmutable() {
        Map<String, Object> metadata =
                metadata(AuditEntryJpaEntity.from(event(false)));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> metadata.put("injected", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static PaymentRegistered event(boolean hasExternalReference) {
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
                PAID_AT,
                PaymentStatus.PAID,
                ACTOR_ID,
                "coach-admin",
                NOW);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(AuditEntryJpaEntity entry) {
        return (Map<String, Object>) ReflectionTestUtils.getField(entry, "metadata");
    }

    private static Object field(AuditEntryJpaEntity entry, String fieldName) {
        return ReflectionTestUtils.getField(entry, fieldName);
    }
}
