package io.github.guillermodubon.coachgym.payment.infrastructure.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDocument;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptOrganization;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptSnapshot;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class PdfBoxPaymentReceiptRendererTest {

    private final PdfBoxPaymentReceiptRenderer renderer = new PdfBoxPaymentReceiptRenderer();

    @Test
    void rendersReadablePdfWithApprovedFinancialAndOrganizationData() throws Exception {
        PaymentReceiptDocument document = renderer.render(snapshot(), organization());

        assertThat(document.bytes()).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        try (var pdf = Loader.loadPDF(document.bytes())) {
            assertThat(pdf.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text)
                    .contains("PAYMENT RECEIPT")
                    .contains("REC-0001")
                    .contains("Coach Gym")
                    .contains("USD 25.00")
                    .contains("CARD")
                    .contains("TEST MODE - NO LIVE CHARGE")
                    .doesNotContain("4111111111111111")
                    .doesNotContain("cvc")
                    .doesNotContain("secret");
        }
    }

    @Test
    void wrapsLongValuesWithoutNetworkOrSharedState() throws Exception {
        String longName = "Long Client ".repeat(15);
        PaymentReceiptSnapshot longSnapshot = new PaymentReceiptSnapshot(
                "REC-LONG", UUID.randomUUID(), "PAY-LONG", PaymentStatus.PAID,
                "CLI-LONG", longName, "MEM-LONG", "Premium Plan", null, 1,
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 10, 10),
                new BigDecimal("30.00"), new BigDecimal("5.00"), new BigDecimal("25.00"),
                "USD", PaymentMethod.CARD,
                Instant.parse("2026-09-11T10:00:00Z"),
                Instant.parse("2026-09-11T10:05:00Z"),
                UUID.randomUUID(), "Staff", false);

        PaymentReceiptDocument document = renderer.render(longSnapshot, organization());

        try (var pdf = Loader.loadPDF(document.bytes())) {
            assertThat(pdf.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            assertThat(new PDFTextStripper().getText(pdf)).contains("Long Client");
        }
    }

    @Test
    void producesStableBytesForTheSameImmutableInput() {
        var snapshot = snapshot();
        assertThat(renderer.render(snapshot, organization()).bytes())
                .isEqualTo(renderer.render(snapshot, organization()).bytes());
    }

    private static PaymentReceiptSnapshot snapshot() {
        return new PaymentReceiptSnapshot(
                "REC-0001", UUID.randomUUID(), "PAY-0001", PaymentStatus.PAID,
                "CLI-0001", "Ana Martinez", "MEM-0001", "Premium", "Intro", 1,
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 10, 10),
                new BigDecimal("30.00"), new BigDecimal("5.00"), new BigDecimal("25.00"),
                "USD", PaymentMethod.CARD,
                Instant.parse("2026-09-11T10:00:00Z"),
                Instant.parse("2026-09-11T10:05:00Z"),
                UUID.randomUUID(), "Ana Admin", true);
    }

    private static PaymentReceiptOrganization organization() {
        return new PaymentReceiptOrganization(
                "Coach Gym", "Coach Gym LLC", "hello@coachgym.test",
                "+50370000000", "San Salvador", "America/El_Salvador");
    }
}
