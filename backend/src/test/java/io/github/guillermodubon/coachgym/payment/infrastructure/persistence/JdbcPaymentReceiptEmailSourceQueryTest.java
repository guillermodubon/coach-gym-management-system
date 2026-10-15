package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.payment.PaymentDetails;
import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDetails;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDocument;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptQuery;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptStorage;
import io.github.guillermodubon.coachgym.payment.application.PaymentStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JdbcPaymentReceiptEmailSourceQueryTest {

    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

    @Mock private PaymentReceiptQuery receiptQuery;
    @Mock private PaymentStore paymentStore;
    @Mock private PaymentReceiptStorage receiptStorage;

    @Test
    void combinesPaymentOwnershipWithTheCanonicalStoredPdf() {
        UUID receiptId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        byte[] bytes = "%PDF-1.7 canonical".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        PaymentReceiptDetails receipt = receipt(receiptId, paymentId, bytes);
        PaymentReceiptDocument document = PaymentReceiptDocument.fromPdfBytes(bytes);
        when(receiptQuery.findById(receiptId)).thenReturn(Optional.of(receipt));
        when(paymentStore.findById(paymentId)).thenReturn(Optional.of(new PaymentDetails(
                paymentId, "PAY-000001", clientId, UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN, "USD", PaymentMethod.CASH, PaymentStatus.PAID,
                null, NOW, UUID.randomUUID(), NOW, NOW, 0)));
        when(receiptStorage.load(
                "receipts/" + receiptId + ".pdf", "application/pdf", receipt.checksumSha256()))
                .thenReturn(document);

        var source = new JdbcPaymentReceiptEmailSourceQuery(
                receiptQuery, paymentStore, receiptStorage).findByReceiptId(receiptId);

        assertThat(source).isPresent();
        assertThat(source.orElseThrow().clientId()).isEqualTo(clientId);
        assertThat(source.orElseThrow().document().bytes()).isEqualTo(bytes);
    }

    @Test
    void doesNotResolveAReceiptWhoseOwningPaymentIsMissing() {
        UUID receiptId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        byte[] bytes = "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(receiptQuery.findById(receiptId))
                .thenReturn(Optional.of(receipt(receiptId, paymentId, bytes)));
        when(paymentStore.findById(paymentId)).thenReturn(Optional.empty());

        assertThat(new JdbcPaymentReceiptEmailSourceQuery(
                receiptQuery, paymentStore, receiptStorage).findByReceiptId(receiptId))
                .isEmpty();
    }

    private static PaymentReceiptDetails receipt(UUID receiptId, UUID paymentId, byte[] bytes) {
        return new PaymentReceiptDetails(
                receiptId, "REC-000001", paymentId, "PAY-000001", PaymentStatus.PAID,
                "CLI-000001", "Ana Client", "MEM-000001", "Monthly", null, 1,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                BigDecimal.valueOf(30), BigDecimal.ZERO, BigDecimal.valueOf(30), "USD",
                PaymentMethod.CASH, NOW.minusSeconds(60), NOW,
                UUID.randomUUID(), "Admin", false, "application/pdf", bytes.length,
                checksum(bytes), "receipt-v1", 0);
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
