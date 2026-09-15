package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentReceiptDetailsTest {

    private static final UUID RECEIPT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000904");
    private static final UUID PAYMENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000905");
    private static final UUID ACTOR_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000906");
    private static final Instant PAID_AT = Instant.parse("2026-09-11T10:00:00Z");
    private static final Instant GENERATED_AT = Instant.parse("2026-09-11T10:05:00Z");

    @Test
    void reconstructsTheSameImmutableRenderingSnapshot() {
        byte[] pdf = "%PDF-1.7\nreceipt".getBytes(StandardCharsets.US_ASCII);
        PaymentReceiptDocument document = PaymentReceiptDocument.fromPdfBytes(pdf);
        PaymentReceiptDetails details = valid(document);

        assertThat(details.contentType()).isEqualTo("application/pdf");
        assertThat(details.sizeBytes()).isEqualTo(document.sizeBytes());
        assertThat(details.checksumSha256()).isEqualTo(document.checksumSha256());
        assertThat(details.snapshot().amount()).isEqualByComparingTo("25.00");
        assertThat(details.snapshot().currency()).isEqualTo("USD");
    }

    @Test
    void rejectsInvalidDocumentMetadataAndNegativeVersion() {
        PaymentReceiptDocument document = PaymentReceiptDocument.fromPdfBytes(
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> new PaymentReceiptDetails(
                RECEIPT_ID, "REC-0001", PAYMENT_ID, "PAY-0001", PaymentStatus.PAID,
                "CLI-0001", "Ana Martinez", "MEM-0001", "Premium", null, 1,
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 10, 10),
                new BigDecimal("30.00"), BigDecimal.ZERO, new BigDecimal("30.00"),
                "USD", PaymentMethod.CASH, PAID_AT, GENERATED_AT, ACTOR_ID, null, false,
                "text/plain", document.sizeBytes(), document.checksumSha256(), null, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("application/pdf");

        assertThatThrownBy(() -> new PaymentReceiptDetails(
                RECEIPT_ID, "REC-0001", PAYMENT_ID, "PAY-0001", PaymentStatus.PAID,
                "CLI-0001", "Ana Martinez", "MEM-0001", "Premium", null, 1,
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 10, 10),
                new BigDecimal("30.00"), BigDecimal.ZERO, new BigDecimal("30.00"),
                "USD", PaymentMethod.CASH, PAID_AT, GENERATED_AT, ACTOR_ID, null, false,
                document.contentType(), document.sizeBytes(), document.checksumSha256(), null, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    private static PaymentReceiptDetails valid(PaymentReceiptDocument document) {
        return new PaymentReceiptDetails(
                RECEIPT_ID, "REC-0001", PAYMENT_ID, "PAY-0001", PaymentStatus.PAID,
                "CLI-0001", "Ana Martinez", "MEM-0001", "Premium", "Intro", 1,
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 10, 10),
                new BigDecimal("30.00"), new BigDecimal("5.00"), new BigDecimal("25.00"),
                " usd ", PaymentMethod.CARD, PAID_AT, GENERATED_AT, ACTOR_ID,
                "Ana Admin", true, document.contentType(), document.sizeBytes(),
                document.checksumSha256(), "v1", 0L);
    }
}
