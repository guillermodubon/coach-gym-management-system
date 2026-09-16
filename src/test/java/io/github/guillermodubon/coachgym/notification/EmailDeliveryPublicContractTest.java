package io.github.guillermodubon.coachgym.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValidationException;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class EmailDeliveryPublicContractTest {

    private static final UUID DELIVERY_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000001");
    private static final Instant REQUESTED_AT = Instant.parse("2026-09-15T12:00:00Z");

    @Test
    void supportedDeliveryTypesAndLifecycleStatesAreExplicit() {
        assertThat(EmailDeliveryType.values())
                .containsExactly(EmailDeliveryType.PAYMENT_RECEIPT,
                        EmailDeliveryType.ACCESS_CREDENTIAL);
        assertThat(EmailDeliveryStatus.values())
                .containsExactly(EmailDeliveryStatus.PENDING,
                        EmailDeliveryStatus.SENT,
                        EmailDeliveryStatus.FAILED);
        assertThat(EmailAttemptResult.values())
                .containsExactly(EmailAttemptResult.SENT,
                        EmailAttemptResult.FAILED,
                        EmailAttemptResult.AMBIGUOUS);
    }

    @Test
    void publicModelsAreImmutableRecordsWithoutProviderTypes() {
        List<Class<?>> contracts = List.of(
                EmailAttachment.class,
                EmailMessage.class,
                EmailDeliveryDetails.class,
                EmailDeliveryAttemptDetails.class,
                EmailSendResult.class,
                EmailDeliveryLifecycleEvent.class);

        assertThat(contracts).allSatisfy(type -> {
            assertThat(type.getPackageName()).isEqualTo(
                    "io.github.guillermodubon.coachgym.notification");
            assertThat(type.isRecord()).isTrue();
            Arrays.stream(type.getDeclaredFields())
                    .forEach(field -> assertThat(field.getType().getName())
                            .doesNotStartWith("org.springframework.")
                            .doesNotStartWith("jakarta.")
                            .doesNotStartWith("com.stripe.")
                            .doesNotStartWith("com.sun.mail."));
        });
    }

    @Test
    void attachmentUsesDefensiveCopiesAndVerifiedChecksum() {
        byte[] source = new byte[]{1, 2, 3};
        EmailAttachment attachment = new EmailAttachment(
                "receipt.pdf", "APPLICATION/PDF", source);

        source[0] = 9;
        byte[] returned = attachment.bytes();
        returned[1] = 8;

        assertThat(attachment.bytes()).containsExactly(1, 2, 3);
        assertThat(attachment.contentType()).isEqualTo("application/pdf");
        assertThat(attachment.sizeBytes()).isEqualTo(3);
        assertThat(attachment.toString()).doesNotContain("[1, 2, 3]");
    }

    @Test
    void rejectsUnsafeAttachmentAndMessageValues() {
        assertThatThrownBy(() -> new EmailAttachment(
                "../receipt.pdf", "application/pdf", new byte[]{1}))
                .isInstanceOf(EmailDeliveryValidationException.class);
        assertThatThrownBy(() -> new EmailMessage(
                "staff@example.com",
                "coach@example.com",
                "Coach Gym",
                null,
                "Subject\nInjected",
                "text",
                "<p>html</p>",
                new EmailAttachment("receipt.pdf", "application/pdf", new byte[]{1})))
                .isInstanceOf(EmailDeliveryValidationException.class);
    }

    @Test
    void normalizesRecipientAndMasksItOutsideTheDeliveryBoundary() {
        EmailMessage message = new EmailMessage(
                "  Staff@Example.COM ",
                " Sender@Example.COM ",
                "Coach Gym",
                null,
                "Receipt",
                "Plain text",
                "<p>Plain text</p>",
                new EmailAttachment("receipt.pdf", "application/pdf", new byte[]{1}));

        assertThat(message.recipient()).isEqualTo("staff@example.com");
        assertThat(message.fromAddress()).isEqualTo("sender@example.com");
        assertThat(message.toString()).contains("***@example.com")
                .doesNotContain("staff@example.com");
    }

    @Test
    void deliveryDetailsEnforceLifecycleMetadataAndMaskRecipient() {
        EmailDeliveryDetails pending = pending();

        assertThat(pending.maskedRecipientSnapshot()).isEqualTo("a***@example.com");
        assertThat(pending.toString()).contains("a***@example.com")
                .doesNotContain("ana.client@example.com")
                .doesNotContain(pending.idempotencyKeyDigest());

        assertThatThrownBy(() -> new EmailDeliveryDetails(
                DELIVERY_ID,
                EmailDeliveryType.PAYMENT_RECEIPT,
                SOURCE_ID,
                CLIENT_ID,
                "ana.client@example.com",
                "Receipt",
                "v1",
                "PAYMENT_RECEIPT",
                SOURCE_ID,
                "credential.png",
                "image/png",
                20,
                "a".repeat(64),
                "b".repeat(64),
                EmailDeliveryStatus.PENDING,
                0,
                null,
                null,
                REQUESTED_AT,
                ACTOR_ID,
                null,
                null,
                REQUESTED_AT,
                REQUESTED_AT,
                0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failedAttemptsRequireBoundedSafeFailureMetadata() {
        EmailDeliveryAttemptDetails attempt = new EmailDeliveryAttemptDetails(
                UUID.randomUUID(),
                DELIVERY_ID,
                1,
                EmailAttemptResult.FAILED,
                REQUESTED_AT,
                REQUESTED_AT.plusSeconds(1),
                EmailDeliveryFailureCode.TRANSPORT_REJECTED,
                "SMTP rejected the message.",
                ACTOR_ID);

        assertThat(attempt.failureMessage()).isEqualTo("SMTP rejected the message.");
        assertThatThrownBy(() -> EmailSendResult.failed(
                EmailDeliveryFailureCode.TRANSPORT_REJECTED, "line\nbreak"))
                .isInstanceOf(EmailDeliveryValidationException.class);
        assertThat(EmailSendResult.ambiguous("The transport outcome is unknown.").result())
                .isEqualTo(EmailAttemptResult.AMBIGUOUS);
    }

    @Test
    void sensitiveComponentNamesAreNotPartOfPublicMetadata() {
        String names = List.of(
                        EmailDeliveryDetails.class,
                        EmailDeliveryAttemptDetails.class,
                        EmailMessage.class)
                .stream()
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .map(RecordComponent::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));

        assertThat(names).doesNotContain(
                "password", "secret", "rawtoken", "qrpayload", "cardnumber", "cvc", "pin", "storagepath");
    }

    @Test
    void lifecycleEventExposesOnlySafeOperationalSnapshotFields() {
        String names = Arrays.stream(EmailDeliveryLifecycleEvent.class.getRecordComponents())
                .map(RecordComponent::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));

        assertThat(names).contains(
                "deliveryid", "deliverytype", "sourceresourceid", "clientid",
                "maskedrecipient", "currentstatus", "attemptnumber",
                "failurecode", "actoruserid", "actoridentifier", "occurredat");
        assertThat(names).doesNotContain(
                "body", "bytes", "password", "secret", "rawtoken", "qrpayload",
                "providerresponse", "stacktrace", "storagepath");
    }

    private static EmailDeliveryDetails pending() {
        return new EmailDeliveryDetails(
                DELIVERY_ID,
                EmailDeliveryType.PAYMENT_RECEIPT,
                SOURCE_ID,
                CLIENT_ID,
                "ana.client@example.com",
                "Coach Gym payment receipt",
                "v1",
                "PAYMENT_RECEIPT",
                SOURCE_ID,
                "receipt.pdf",
                "application/pdf",
                20,
                "a".repeat(64),
                "b".repeat(64),
                EmailDeliveryStatus.PENDING,
                0,
                null,
                null,
                REQUESTED_AT,
                ACTOR_ID,
                null,
                null,
                REQUESTED_AT,
                REQUESTED_AT,
                0);
    }
}
