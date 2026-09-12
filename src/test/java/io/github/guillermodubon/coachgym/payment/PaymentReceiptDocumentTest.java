package io.github.guillermodubon.coachgym.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PaymentReceiptDocumentTest {

    @Test
    void computesChecksumAndDefensivelyCopiesPdfBytes() {
        byte[] source = "%PDF-1.7\nreceipt".getBytes(StandardCharsets.US_ASCII);
        PaymentReceiptDocument document = PaymentReceiptDocument.fromPdfBytes(source);

        source[0] = 'X';
        byte[] returned = document.bytes();
        returned[1] = 'X';

        assertThat(document.contentType()).isEqualTo("application/pdf");
        assertThat(document.sizeBytes()).isEqualTo(16);
        assertThat(document.bytes()[0]).isEqualTo((byte) '%');
        assertThat(document.bytes()[1]).isEqualTo((byte) 'P');
        assertThat(document.checksumSha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void normalizesContentTypeAndRejectsInvalidChecksumOrMediaType() {
        byte[] bytes = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        String checksum = PaymentReceiptDocument.fromPdfBytes(bytes).checksumSha256();

        PaymentReceiptDocument normalized = new PaymentReceiptDocument(
                " APPLICATION/PDF ", bytes, checksum.toUpperCase());
        assertThat(normalized.contentType()).isEqualTo("application/pdf");
        assertThat(normalized.checksumSha256()).isEqualTo(checksum);

        assertThatThrownBy(() -> new PaymentReceiptDocument(
                "text/plain", bytes, checksum))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("application/pdf");
        assertThatThrownBy(() -> new PaymentReceiptDocument(
                "application/pdf", bytes, "0".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }
}
