package io.github.guillermodubon.coachgym.payment.infrastructure.pdf;

import io.github.guillermodubon.coachgym.payment.PaymentReceiptDocument;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptOrganization;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptSnapshot;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptRenderException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptRenderer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

@Component
class PdfBoxPaymentReceiptRenderer implements PaymentReceiptRenderer {

    private static final String RENDERER_VERSION = "pdfbox-3.0.8";
    private static final float LEFT_MARGIN = 54;
    private static final float TOP = 742;
    private static final float LINE_HEIGHT = 15;
    private static final int LINES_PER_PAGE = 43;
    private static final int MAX_LINE_LENGTH = 92;
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.ROOT);

    @Override
    public String rendererVersion() {
        return RENDERER_VERSION;
    }

    @Override
    public PaymentReceiptDocument render(
            PaymentReceiptSnapshot snapshot,
            PaymentReceiptOrganization organization) {
        if (snapshot == null || organization == null) {
            throw new IllegalArgumentException("Receipt rendering input is required.");
        }
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDDocumentInformation information = document.getDocumentInformation();
            information.setTitle("Payment Receipt " + snapshot.receiptNumber());
            information.setAuthor(pdfSafe(organization.displayName()));
            information.setSubject("Coach Gym payment receipt");
            information.setCreator("Coach Gym");
            document.setDocumentId(snapshot.paymentId().getMostSignificantBits()
                    ^ snapshot.paymentId().getLeastSignificantBits());

            List<Line> lines = lines(snapshot, organization);
            int pageCount = Math.max(1, (lines.size() + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    int start = pageIndex * LINES_PER_PAGE;
                    int end = Math.min(lines.size(), start + LINES_PER_PAGE);
                    float y = TOP;
                    if (pageIndex > 0) {
                        write(stream, new Line(
                                organization.displayName() + " - PAYMENT RECEIPT (CONTINUED)",
                                true, 10), y);
                        y -= LINE_HEIGHT * 2;
                    }
                    for (int lineIndex = start; lineIndex < end; lineIndex++) {
                        Line line = lines.get(lineIndex);
                        write(stream, line, y);
                        y -= line.fontSize() >= 15 ? LINE_HEIGHT + 3 : LINE_HEIGHT;
                    }
                }
            }
            document.save(output);
            return PaymentReceiptDocument.fromPdfBytes(output.toByteArray());
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof PaymentReceiptRenderException renderException) {
                throw renderException;
            }
            throw new PaymentReceiptRenderException(
                    "Payment receipt document could not be rendered.", exception);
        }
    }

    private static List<Line> lines(
            PaymentReceiptSnapshot snapshot,
            PaymentReceiptOrganization organization) {
        List<Line> lines = new ArrayList<>();
        add(lines, organization.displayName(), true, 18);
        add(lines, "PAYMENT RECEIPT", true, 16);
        add(lines, "Receipt number: " + snapshot.receiptNumber(), true, 11);
        add(lines, "");
        addOptional(lines, organization.legalName(), "Legal name: ");
        addOptional(lines, organization.email(), "Email: ");
        addOptional(lines, organization.phone(), "Phone: ");
        addOptional(lines, organization.address(), "Address: ");
        add(lines, "");
        add(lines, "PAYMENT CONFIRMED", true, 12);
        add(lines, "Payment code: " + snapshot.paymentCode());
        add(lines, "Paid at: " + DATE_TIME_FORMAT.withZone(organization.zoneId()).format(snapshot.paidAt()));
        add(lines, "Generated at: " + DATE_TIME_FORMAT.withZone(organization.zoneId()).format(snapshot.generatedAt()));
        add(lines, "Payment method: " + displayMethod(snapshot));
        if (snapshot.testMode()) {
            add(lines, "TEST MODE - NO LIVE CHARGE", true, 11);
        }
        add(lines, "");
        add(lines, "CLIENT AND MEMBERSHIP", true, 12);
        add(lines, "Client code: " + snapshot.clientCode());
        add(lines, "Client name: " + snapshot.clientDisplayName());
        add(lines, "Membership code: " + snapshot.membershipCode());
        add(lines, "Plan: " + snapshot.planName());
        add(lines, "Membership period: " + snapshot.membershipPeriodNumber()
                + " (" + snapshot.periodStartsOn() + " to " + snapshot.periodEndsOn() + ")");
        addOptional(lines, snapshot.promotionName(), "Promotion: ");
        add(lines, "");
        add(lines, "AMOUNT", true, 12);
        add(lines, "List price: " + money(snapshot.currency(), snapshot.listPrice()));
        add(lines, "Discount: " + money(snapshot.currency(), snapshot.discountAmount()));
        add(lines, "TOTAL PAID: " + money(snapshot.currency(), snapshot.amount()), true, 15);
        return lines;
    }

    private static String displayMethod(PaymentReceiptSnapshot snapshot) {
        return snapshot.paymentMethod().name().replace('_', ' ');
    }

    private static String money(String currency, java.math.BigDecimal amount) {
        return currency + " " + amount.setScale(2).toPlainString();
    }

    private static void addOptional(List<Line> lines, String value, String label) {
        if (value != null && !value.isBlank()) {
            add(lines, label + value);
        }
    }

    private static void add(List<Line> lines, String value) {
        add(lines, value, false, 10);
    }

    private static void add(List<Line> lines, String value, boolean bold, float fontSize) {
        String normalized = value == null ? "" : value;
        if (normalized.isEmpty()) {
            lines.add(new Line("", bold, fontSize));
            return;
        }
        String safe = pdfSafe(normalized);
        for (int offset = 0; offset < safe.length(); offset += MAX_LINE_LENGTH) {
            lines.add(new Line(
                    safe.substring(offset, Math.min(safe.length(), offset + MAX_LINE_LENGTH)),
                    bold, fontSize));
        }
    }

    private static void write(PDPageContentStream stream, Line line, float y) throws IOException {
        PDType1Font font = new PDType1Font(line.bold()
                ? Standard14Fonts.FontName.HELVETICA_BOLD
                : Standard14Fonts.FontName.HELVETICA);
        stream.beginText();
        stream.setFont(font, line.fontSize());
        stream.newLineAtOffset(LEFT_MARGIN, y);
        stream.showText(line.text());
        stream.endText();
    }

    private static String pdfSafe(String value) {
        StringBuilder result = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (codePoint >= 32 && codePoint <= 255 && codePoint != 127) {
                result.appendCodePoint(codePoint);
            } else {
                result.append('?');
            }
        });
        return result.toString();
    }

    private record Line(String text, boolean bold, float fontSize) {
    }
}
