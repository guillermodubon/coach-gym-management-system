package io.github.guillermodubon.coachgym.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryIdempotency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmailDeliveryIdempotencyTest {

    private static final UUID SOURCE_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001");

    @Test
    void derivesStableDigestFromServerControlledLogicalComponents() {
        String first = EmailDeliveryIdempotency.derive(
                EmailDeliveryType.PAYMENT_RECEIPT,
                SOURCE_ID,
                " Ana.Client@Example.com ",
                "v1");
        String second = EmailDeliveryIdempotency.derive(
                EmailDeliveryType.PAYMENT_RECEIPT,
                SOURCE_ID,
                "ana.client@example.com",
                "v1");

        assertThat(first).hasSize(64).matches("[0-9a-f]{64}").isEqualTo(second);
        assertThat(first).doesNotContain("ana", "example");
    }

    @Test
    void changesToTypeSourceRecipientOrTemplateProduceDifferentLogicalDeliveries() {
        String baseline = EmailDeliveryIdempotency.derive(
                EmailDeliveryType.PAYMENT_RECEIPT, SOURCE_ID,
                "ana@example.com", "v1");

        assertThat(EmailDeliveryIdempotency.derive(
                EmailDeliveryType.ACCESS_CREDENTIAL, SOURCE_ID,
                "ana@example.com", "v1")).isNotEqualTo(baseline);
        assertThat(EmailDeliveryIdempotency.derive(
                EmailDeliveryType.PAYMENT_RECEIPT, UUID.randomUUID(),
                "ana@example.com", "v1")).isNotEqualTo(baseline);
        assertThat(EmailDeliveryIdempotency.derive(
                EmailDeliveryType.PAYMENT_RECEIPT, SOURCE_ID,
                "ana@example.com", "v2")).isNotEqualTo(baseline);
    }

    @Test
    void rejectsMissingOrInvalidServerKeyComponents() {
        assertThatThrownBy(() -> EmailDeliveryIdempotency.derive(
                null, SOURCE_ID, "ana@example.com", "v1"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> EmailDeliveryIdempotency.derive(
                EmailDeliveryType.PAYMENT_RECEIPT, SOURCE_ID, "not-an-email", "v1"))
                .isInstanceOf(RuntimeException.class);
    }
}
