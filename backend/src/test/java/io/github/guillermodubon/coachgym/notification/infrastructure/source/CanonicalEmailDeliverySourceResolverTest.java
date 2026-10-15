package io.github.guillermodubon.coachgym.notification.infrastructure.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDetails;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialEmailSource;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialEmailSourceQuery;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDocument;
import io.github.guillermodubon.coachgym.client.ClientDetails;
import io.github.guillermodubon.coachgym.client.ClientQuery;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.notification.EmailDeliverySource;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryAttachmentException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryRecipientUnavailableException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliverySourceNotFoundException;
import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDetails;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDocument;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptEmailSource;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptEmailSourceQuery;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CanonicalEmailDeliverySourceResolverTest {

    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

    @Mock private PaymentReceiptEmailSourceQuery receiptQuery;
    @Mock private AccessCredentialEmailSourceQuery credentialQuery;
    @Mock private ClientQuery clientQuery;

    private CanonicalEmailDeliverySourceResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CanonicalEmailDeliverySourceResolver(
                receiptQuery, credentialQuery, clientQuery);
    }

    @Test
    void resolvesAuthoritativeRecipientAndExactCanonicalPdf() {
        UUID receiptId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        byte[] pdf = "%PDF-1.7\ncanonical".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        PaymentReceiptDetails details = receipt(receiptId, paymentId, pdf);
        when(receiptQuery.findByReceiptId(receiptId))
                .thenReturn(Optional.of(new PaymentReceiptEmailSource(
                        details, clientId, PaymentReceiptDocument.fromPdfBytes(pdf))));
        when(clientQuery.findClientById(clientId)).thenReturn(Optional.of(client(clientId, " Ana@Example.COM ")));

        EmailDeliverySource result = resolver.resolvePaymentReceipt(receiptId);

        assertThat(result.clientId()).isEqualTo(clientId);
        assertThat(result.recipient()).isEqualTo("ana@example.com");
        assertThat(result.attachment().filename()).isEqualTo("payment-receipt-" + receiptId + ".pdf");
        assertThat(result.attachment().bytes()).isEqualTo(pdf);
        assertThat(result.attachment().checksumSha256()).isEqualTo(details.checksumSha256());
    }

    @Test
    void resolvesReceiptFromItsPaymentAndUsesTheReceiptAsTheSourceIdentity() {
        UUID receiptId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        byte[] pdf = "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        PaymentReceiptDetails details = receipt(receiptId, paymentId, pdf);
        when(receiptQuery.findByPaymentId(paymentId))
                .thenReturn(Optional.of(new PaymentReceiptEmailSource(details, clientId,
                        PaymentReceiptDocument.fromPdfBytes(pdf))));
        when(clientQuery.findClientById(clientId)).thenReturn(Optional.of(client(clientId, "client@example.com")));

        EmailDeliverySource result = resolver.resolvePaymentReceiptForPayment(paymentId);

        assertThat(result.sourceResourceId()).isEqualTo(receiptId);
        assertThat(result.deliveryType()).isEqualTo(EmailDeliveryType.PAYMENT_RECEIPT);
    }

    @Test
    void resolvesActiveCredentialAndExactCanonicalPng() {
        UUID credentialId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01};
        AccessCredentialDetails details = credential(credentialId, clientId);
        AccessCredentialDocument document = new AccessCredentialDocument("image/png", png);
        String checksum = checksum(png);
        when(credentialQuery.findByCredentialId(credentialId))
                .thenReturn(Optional.of(new AccessCredentialEmailSource(
                        details, "image/png", png.length, checksum, "qr-v1", document)));
        when(clientQuery.findClientById(clientId)).thenReturn(Optional.of(client(clientId, "client@example.com")));

        EmailDeliverySource result = resolver.resolveAccessCredential(credentialId);

        assertThat(result.recipient()).isEqualTo("client@example.com");
        assertThat(result.attachment().filename()).isEqualTo("access-credential-" + credentialId + ".png");
        assertThat(result.attachment().bytes()).isEqualTo(png);
        assertThat(result.attachment().contentType()).isEqualTo("image/png");
    }

    @Test
    void rejectsMissingEmailWithoutAcceptingAnOverride() {
        UUID receiptId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        byte[] pdf = "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(receiptQuery.findByReceiptId(receiptId))
                .thenReturn(Optional.of(new PaymentReceiptEmailSource(
                        receipt(receiptId, pdf), clientId, PaymentReceiptDocument.fromPdfBytes(pdf))));
        when(clientQuery.findClientById(clientId)).thenReturn(Optional.of(client(clientId, null)));

        assertThatThrownBy(() -> resolver.resolvePaymentReceipt(receiptId))
                .isInstanceOf(EmailDeliveryRecipientUnavailableException.class);
    }

    @Test
    void treatsMissingAndCorruptSourcesAsSafeFailures() {
        UUID id = UUID.randomUUID();
        when(receiptQuery.findByReceiptId(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> resolver.resolvePaymentReceipt(id))
                .isInstanceOf(EmailDeliverySourceNotFoundException.class);

        when(receiptQuery.findByReceiptId(id)).thenThrow(new IllegalArgumentException("corrupt"));
        assertThatThrownBy(() -> resolver.resolvePaymentReceipt(id))
                .isInstanceOf(EmailDeliveryAttachmentException.class);
    }

    @Test
    void rejectsAClientProjectionThatDoesNotMatchTheAuthoritativeSource() {
        UUID receiptId = UUID.randomUUID();
        UUID sourceClientId = UUID.randomUUID();
        UUID otherClientId = UUID.randomUUID();
        byte[] pdf = "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(receiptQuery.findByReceiptId(receiptId))
                .thenReturn(Optional.of(new PaymentReceiptEmailSource(
                        receipt(receiptId, pdf), sourceClientId, PaymentReceiptDocument.fromPdfBytes(pdf))));
        when(clientQuery.findClientById(sourceClientId))
                .thenReturn(Optional.of(client(otherClientId, "other@example.com")));

        assertThatThrownBy(() -> resolver.resolvePaymentReceipt(receiptId))
                .isInstanceOf(EmailDeliverySourceNotFoundException.class);
    }

    private static ClientDetails client(UUID id, String email) {
        return new ClientDetails(id, "CLI-000001", "Ana", "Client", email,
                "+50370000000", LocalDate.of(1995, 4, 12), ClientStatus.ACTIVE,
                NOW.minusSeconds(60), NOW, null);
    }

    private static PaymentReceiptDetails receipt(UUID id) {
        byte[] pdf = "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        return receipt(id, UUID.randomUUID(), pdf);
    }

    private static PaymentReceiptDetails receipt(UUID id, byte[] pdf) {
        return receipt(id, UUID.randomUUID(), pdf);
    }

    private static PaymentReceiptDetails receipt(UUID id, UUID paymentId, byte[] pdf) {
        return new PaymentReceiptDetails(
                id, "REC-000001", paymentId, "PAY-000001", PaymentStatus.PAID,
                "CLI-000001", "Ana Client", "MEM-000001", "Monthly", null, 1,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                BigDecimal.valueOf(30), BigDecimal.ZERO, BigDecimal.valueOf(30), "USD",
                PaymentMethod.CASH, NOW.minusSeconds(120), NOW.minusSeconds(60),
                UUID.randomUUID(), "Admin", false, "application/pdf", pdf.length,
                checksum(pdf), "receipt-v1", 0);
    }

    private static AccessCredentialDetails credential(UUID id, UUID clientId) {
        return new AccessCredentialDetails(id, clientId, "QR-000001",
                AccessCredentialStatus.ACTIVE, "v1", NOW.minusSeconds(60),
                UUID.randomUUID(), null, null, null, 0);
    }

    private static String checksum(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
