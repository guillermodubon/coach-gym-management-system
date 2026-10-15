package io.github.guillermodubon.coachgym.notification.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryDetails;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryStatus;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmailDeliveryResponseTest {

    @Test
    void masksRecipientAndOmitsSensitiveOperationalFields() {
        UUID deliveryId = UUID.randomUUID();
        EmailDeliveryResponse response = EmailDeliveryResponse.from(failed(deliveryId));

        assertThat(response.maskedRecipient()).isEqualTo("a***@example.com");
        assertThat(response.lastFailureCode()).isEqualTo(EmailDeliveryFailureCode.TRANSPORT_TIMEOUT);
        assertThat(Arrays.stream(EmailDeliveryResponse.class.getRecordComponents())
                .map(component -> component.getName())
                .toList())
                .doesNotContain(
                        "recipientSnapshot",
                        "lastFailureMessage",
                        "attachmentChecksumSha256",
                        "idempotencyKeyDigest");
    }

    @Test
    void omitsProviderMessageAndFailureTextFromAttemptProjection() {
        assertThat(Arrays.stream(EmailDeliveryAttemptResponse.class.getRecordComponents())
                .map(component -> component.getName())
                .toList())
                .doesNotContain("providerMessageId", "failureMessage");
    }

    private static EmailDeliveryDetails failed(UUID id) {
        Instant requested = Instant.parse("2026-09-15T12:00:00Z");
        return new EmailDeliveryDetails(
                id,
                EmailDeliveryType.PAYMENT_RECEIPT,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "alice@example.com",
                "Coach Gym payment receipt",
                "v1",
                "PAYMENT_RECEIPT",
                UUID.randomUUID(),
                "receipt.pdf",
                "application/pdf",
                128,
                "a".repeat(64),
                "b".repeat(64),
                EmailDeliveryStatus.FAILED,
                1,
                EmailDeliveryFailureCode.TRANSPORT_TIMEOUT,
                "The provider timed out.",
                requested,
                UUID.randomUUID(),
                null,
                requested.plusSeconds(1),
                requested,
                requested.plusSeconds(1),
                1);
    }
}
