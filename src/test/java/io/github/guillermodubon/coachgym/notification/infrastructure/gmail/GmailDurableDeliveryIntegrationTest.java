package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import io.github.guillermodubon.coachgym.notification.ComposedEmail;
import io.github.guillermodubon.coachgym.notification.EmailAttachment;
import io.github.guillermodubon.coachgym.notification.EmailAttemptResult;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryDetails;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailDeliverySource;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryStatus;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.application.EmailComposer;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryQuery;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliverySourceResolver;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryStateConflictException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryStore;
import io.github.guillermodubon.coachgym.notification.application.RequestAccessCredentialEmailCommand;
import io.github.guillermodubon.coachgym.notification.application.RequestPaymentReceiptEmailCommand;
import io.github.guillermodubon.coachgym.notification.application.RetryEmailDeliveryCommand;
import io.github.guillermodubon.coachgym.notification.application.TransactionalEmailDeliveryApplicationService;
import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryLifecyclePolicy;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

class GmailDurableDeliveryIntegrationTest extends AbstractIncidentApiIntegrationTest {

    private static final UUID INITIAL_BRANCH_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000002");
    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    private static final byte[] CANONICAL_PDF =
            "%PDF-1.7\ncanonical-receipt".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CANONICAL_PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x01};

    @Autowired private EmailDeliveryStore deliveryStore;
    @Autowired private EmailDeliveryQuery deliveryQuery;
    @Autowired private ApplicationEventPublisher eventPublisher;

    private GmailHttpStub gmail;
    private EmailDeliverySourceResolver sourceResolver;
    private EmailDeliverySource receiptSource;
    private EmailDeliverySource credentialSource;
    private TransactionalEmailDeliveryApplicationService service;
    private UUID paymentId;
    private UUID clientId;
    private String recipientEmail;

    @BeforeEach
    void prepareGmailBackedDurableService() throws Exception {
        jdbcTemplate.execute("truncate table gym.email_delivery_claims, "
                + "gym.email_delivery_attempts, gym.email_deliveries");
        gmail = GmailHttpStub.start();

        clientId = UUID.randomUUID();
        recipientEmail = "canonical+" + clientId + "@example.test";
        jdbcTemplate.update("""
                insert into gym.clients
                    (id, first_name, last_name, email, phone, status, version)
                values (?, 'Email', 'Recipient', ?, '+50370000000', 'ACTIVE', 0)
                """, clientId, recipientEmail);
        paymentId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        receiptSource = source(
                EmailDeliveryType.PAYMENT_RECEIPT, receiptId, recipientEmail,
                "payment-receipt-" + receiptId + ".pdf", "application/pdf", CANONICAL_PDF);
        credentialSource = source(
                EmailDeliveryType.ACCESS_CREDENTIAL, credentialId, recipientEmail,
                "access-credential-" + credentialId + ".png", "image/png", CANONICAL_PNG);

        sourceResolver = mock(EmailDeliverySourceResolver.class);
        when(sourceResolver.resolvePaymentReceiptForPayment(paymentId)).thenReturn(receiptSource);
        when(sourceResolver.resolveCurrentAccessCredentialForClient(clientId))
                .thenReturn(credentialSource);
        when(sourceResolver.resolve(EmailDeliveryType.PAYMENT_RECEIPT, receiptId))
                .thenReturn(receiptSource);
        when(sourceResolver.resolve(EmailDeliveryType.ACCESS_CREDENTIAL, credentialId))
                .thenReturn(credentialSource);

        EmailComposer composer = source -> new ComposedEmail("gmail-flow-v1",
                new EmailMessage(
                        source.recipient(),
                        GmailHttpStub.SENDER,
                        "Coach Gym",
                        null,
                        source.deliveryType() == EmailDeliveryType.PAYMENT_RECEIPT
                                ? "Payment receipt" : "Access credential",
                        "Your canonical " + source.deliveryType().name().toLowerCase() + " is attached.",
                        "<p>Your canonical " + source.deliveryType().name().toLowerCase()
                                + " is attached.</p>",
                        source.attachment()));
        service = new TransactionalEmailDeliveryApplicationService(
                sourceResolver,
                composer,
                gmail.sender(),
                deliveryStore,
                deliveryQuery,
                new EmailDeliveryLifecyclePolicy(2),
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void closeGmailStub() {
        if (gmail != null) {
            gmail.close();
        }
    }

    @Test
    void receiptAndCredentialPersistSentAttemptsIdempotentlyWithCanonicalAttachments()
            throws Exception {
        EmailDeliveryDetails receipt = service.requestPaymentReceiptEmail(
                new RequestPaymentReceiptEmailCommand(paymentId), actor());
        EmailDeliveryDetails credential = service.requestAccessCredentialEmail(
                new RequestAccessCredentialEmailCommand(clientId), actor());

        assertThat(receipt.status()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(credential.status()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(deliveryQuery.findAttempts(receipt.id()))
                .singleElement()
                .satisfies(attempt -> {
                    assertThat(attempt.result()).isEqualTo(EmailAttemptResult.SENT);
                    assertThat(attempt.providerMessageId()).isEqualTo("stub-message-1");
                });
        assertThat(deliveryQuery.findAttempts(credential.id()))
                .singleElement()
                .extracting(attempt -> attempt.providerMessageId())
                .isEqualTo("stub-message-2");

        MimeMessage receiptMessage = parse(gmail.decodeRawMessage(0));
        MimeMessage credentialMessage = parse(gmail.decodeRawMessage(1));
        Multipart receiptParts = (Multipart) receiptMessage.getContent();
        Multipart credentialParts = (Multipart) credentialMessage.getContent();
        assertThat(receiptMessage.getRecipients(jakarta.mail.Message.RecipientType.TO)[0].toString())
                .contains(recipientEmail);
        assertThat(receiptParts.getBodyPart(1).getFileName()).isEqualTo(receiptSource.attachment().filename());
        assertThat(receiptParts.getBodyPart(1).getInputStream().readAllBytes())
                .containsExactly(CANONICAL_PDF);
        assertThat(credentialParts.getBodyPart(1).getFileName())
                .isEqualTo(credentialSource.attachment().filename());
        assertThat(credentialParts.getBodyPart(1).getContentType().toLowerCase())
                .startsWith("image/png");
        assertThat(credentialParts.getBodyPart(1).getInputStream().readAllBytes())
                .containsExactly(CANONICAL_PNG);

        EmailDeliveryDetails repeatedReceipt = service.requestPaymentReceiptEmail(
                new RequestPaymentReceiptEmailCommand(paymentId), actor());
        assertThat(repeatedReceipt.id()).isEqualTo(receipt.id());
        assertThat(gmail.sendCount()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from gym.email_deliveries", Integer.class)).isEqualTo(2);
    }

    @Test
    void definitiveFailureCanBeExplicitlyRetriedButAmbiguousOutcomeCannot()
            throws Exception {
        gmail.respondToNextSend(400, "private provider error must not be persisted");
        EmailDeliveryDetails failedReceipt = service.requestPaymentReceiptEmail(
                new RequestPaymentReceiptEmailCommand(paymentId), actor());

        assertThat(failedReceipt.status()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(failedReceipt.lastFailureCode())
                .isEqualTo(EmailDeliveryFailureCode.TRANSPORT_REJECTED);
        assertThat(failedReceipt.lastFailureMessage())
                .doesNotContain("private provider error");
        assertThat(deliveryQuery.findAttempts(failedReceipt.id()))
                .singleElement()
                .satisfies(attempt -> {
                    assertThat(attempt.result()).isEqualTo(EmailAttemptResult.FAILED);
                    assertThat(attempt.failureCode())
                            .isEqualTo(EmailDeliveryFailureCode.TRANSPORT_REJECTED);
                });
        assertThat(failedReceipt.attachmentChecksumSha256())
                .isEqualTo(receiptSource.attachment().checksumSha256());
        assertThat(receiptSource.attachment().bytes()).containsExactly(CANONICAL_PDF);

        EmailDeliveryDetails retried = service.retryEmailDelivery(
                new RetryEmailDeliveryCommand(failedReceipt.id(), failedReceipt.version()), actor());
        assertThat(retried.status()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(retried.attemptCount()).isEqualTo(2);
        assertThat(deliveryQuery.findAttempts(retried.id()))
                .extracting(attempt -> attempt.result())
                .containsExactly(EmailAttemptResult.FAILED, EmailAttemptResult.SENT);

        gmail.respondToNextSend(503, "provider body is not trusted");
        EmailDeliveryDetails ambiguous = service.requestAccessCredentialEmail(
                new RequestAccessCredentialEmailCommand(clientId), actor());
        assertThat(ambiguous.status()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(ambiguous.lastFailureCode())
                .isEqualTo(EmailDeliveryFailureCode.AMBIGUOUS_TRANSPORT_OUTCOME);
        assertThat(deliveryQuery.findAttempts(ambiguous.id()))
                .singleElement()
                .satisfies(attempt -> assertThat(attempt.result())
                        .isEqualTo(EmailAttemptResult.AMBIGUOUS));

        int sendsBeforeRejectedRetry = gmail.sendCount();
        assertThatThrownBy(() -> service.retryEmailDelivery(
                new RetryEmailDeliveryCommand(ambiguous.id(), ambiguous.version()), actor()))
                .isInstanceOf(EmailDeliveryStateConflictException.class);
        assertThat(gmail.sendCount()).isEqualTo(sendsBeforeRejectedRetry);
        assertThat(gmail.requests()).allSatisfy(request ->
                assertThat(request.toString()).doesNotContain("private provider error", "provider body"));
    }

    private EmailDeliverySource source(
            EmailDeliveryType type,
            UUID sourceId,
            String recipient,
            String filename,
            String contentType,
            byte[] bytes) {
        return new EmailDeliverySource(
                type,
                sourceId,
                clientId,
                recipient,
                new EmailAttachment(filename, contentType, bytes),
                io.github.guillermodubon.coachgym.notification.EmailDeliveryTemplateData.empty(),
                INITIAL_BRANCH_ID);
    }

    private AuthenticatedActor actor() {
        return new AuthenticatedActor(adminId, ADMIN_USERNAME);
    }

    private static MimeMessage parse(byte[] mime) throws Exception {
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(mime));
    }
}
