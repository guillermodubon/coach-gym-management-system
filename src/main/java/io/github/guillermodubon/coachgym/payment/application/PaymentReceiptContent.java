package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentReceiptDetails;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDocument;
import java.util.Objects;

/** Canonical receipt metadata and its validated PDF content for the web boundary. */
public record PaymentReceiptContent(
        PaymentReceiptDetails details,
        PaymentReceiptDocument document) {

    public PaymentReceiptContent {
        Objects.requireNonNull(details, "Receipt details are required.");
        Objects.requireNonNull(document, "Receipt document is required.");
    }
}
