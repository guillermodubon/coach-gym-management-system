package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.payment.PaymentAttemptCreated;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptFailureCode;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptProviderStatusChanged;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatus;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatusChanged;
import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import io.github.guillermodubon.coachgym.payment.PaymentProviderEventAcknowledged;
import io.github.guillermodubon.coachgym.payment.PaymentProviderPaymentConfirmed;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PaymentAttemptAuditEntryJpaEntityTest {

    private static final UUID ATTEMPT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000701");
    private static final UUID PAYMENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000702");
    private static final UUID CLIENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000703");
    private static final UUID MEMBERSHIP_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000704");
    private static final UUID PERIOD_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000705");
    private static final UUID ACTOR_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000706");
    private static final Instant NOW = Instant.parse("2026-09-10T16:00:00Z");

    @Test
    void recordsAttemptCreationWithSafeLineageMetadata() {
        AuditEntryJpaEntity entry = AuditEntryJpaEntity.from(
                new PaymentAttemptCreated(
                        ATTEMPT_ID, CLIENT_ID, MEMBERSHIP_ID, PERIOD_ID,
                        PaymentProvider.STRIPE, new BigDecimal("25.00"),
                        "USD", ACTOR_ID, "coach-admin", NOW));

        assertThat(field(entry, "actionCode")).isEqualTo("PAYMENT_ATTEMPT_CREATED");
        assertThat(field(entry, "resourceType")).isEqualTo("PAYMENT_ATTEMPT");
        assertThat(field(entry, "resourceId")).isEqualTo(ATTEMPT_ID);
        assertThat(field(entry, "actorUserId")).isEqualTo(ACTOR_ID);
        assertThat(metadata(entry)).containsEntry("provider", "STRIPE")
                .containsEntry("expectedAmount", "25.00")
                .containsEntry("currency", "USD");
    }

    @Test
    void recordsStaffCancellationWithoutProviderReferences() {
        AuditEntryJpaEntity entry = AuditEntryJpaEntity.from(
                new PaymentAttemptStatusChanged(
                        ATTEMPT_ID, PaymentProvider.STRIPE,
                        PaymentAttemptStatus.PROCESSING,
                        PaymentAttemptStatus.CANCELLED,
                        PaymentAttemptFailureCode.PROVIDER_CANCELLED,
                        null, ACTOR_ID, NOW));

        assertThat(field(entry, "actionCode")).isEqualTo("PAYMENT_ATTEMPT_CANCELLED");
        assertThat(field(entry, "actorIdentifierSnapshot")).isEqualTo("staff");
        assertThat(metadata(entry)).containsEntry("newStatus", "CANCELLED")
                .containsEntry("failureCode", "PROVIDER_CANCELLED")
                .containsEntry("confirmedPaymentPresent", false);
    }

    @Test
    void recordsVerifiedOutcomeAndMaterializedPaymentSeparately() {
        AuditEntryJpaEntity outcome = AuditEntryJpaEntity.from(
                new PaymentAttemptProviderStatusChanged(
                        ATTEMPT_ID, PaymentProvider.STRIPE,
                        PaymentAttemptStatus.PROCESSING,
                        PaymentAttemptStatus.SUCCEEDED, null, PAYMENT_ID,
                        true, NOW));
        AuditEntryJpaEntity payment = AuditEntryJpaEntity.from(
                new PaymentProviderPaymentConfirmed(
                        ATTEMPT_ID, PAYMENT_ID, PaymentProvider.STRIPE,
                        new BigDecimal("25.00"), "USD", NOW));

        assertThat(field(outcome, "actionCode"))
                .isEqualTo("PAYMENT_ATTEMPT_PROVIDER_SUCCESS");
        assertThat(field(payment, "actionCode"))
                .isEqualTo("PAYMENT_PROVIDER_PAYMENT_CONFIRMED");
        assertThat(field(payment, "resourceType")).isEqualTo("PAYMENT");
        assertThat(metadata(payment)).containsEntry("paymentAttemptId", ATTEMPT_ID.toString())
                .containsEntry("amount", "25.00")
                .doesNotContainKey("providerPaymentReference");
    }

    @Test
    void recordsDuplicateAcknowledgementWithoutEventIdentity() {
        AuditEntryJpaEntity entry = AuditEntryJpaEntity.from(
                new PaymentProviderEventAcknowledged(
                        ATTEMPT_ID, PaymentProvider.STRIPE,
                        "CHECKOUT_COMPLETED", "PROCESSED", true, NOW));

        assertThat(field(entry, "actionCode"))
                .isEqualTo("PAYMENT_PROVIDER_EVENT_DUPLICATE_ACKNOWLEDGED");
        assertThat(metadata(entry)).containsEntry("duplicate", true)
                .containsEntry("processingResult", "PROCESSED")
                .doesNotContainKey("providerEventReference");
    }

    @Test
    void auditMetadataIsImmutableAndContainsNoSensitiveProviderValues() {
        Map<String, Object> metadata = metadata(AuditEntryJpaEntity.from(
                new PaymentAttemptProviderStatusChanged(
                        ATTEMPT_ID, PaymentProvider.STRIPE,
                        PaymentAttemptStatus.PROCESSING,
                        PaymentAttemptStatus.FAILED,
                        PaymentAttemptFailureCode.PROVIDER_DECLINED,
                        null, true, NOW)));

        assertThat(metadata).doesNotContainKey("payload")
                .doesNotContainKey("signature")
                .doesNotContainKey("secret")
                .doesNotContainKey("cardNumber")
                .doesNotContainKey("checkoutUrl");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> metadata.put("payload", "secret"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(AuditEntryJpaEntity entry) {
        return (Map<String, Object>) ReflectionTestUtils.getField(entry, "metadata");
    }

    private static Object field(AuditEntryJpaEntity entry, String name) {
        return ReflectionTestUtils.getField(entry, name);
    }
}
