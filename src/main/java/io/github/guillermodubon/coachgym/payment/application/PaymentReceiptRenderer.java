package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentReceiptDocument;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptOrganization;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptSnapshot;

/** Technology-neutral port for rendering an immutable receipt document. */
public interface PaymentReceiptRenderer {

    PaymentReceiptDocument render(
            PaymentReceiptSnapshot snapshot,
            PaymentReceiptOrganization organization);

    /** Stable renderer metadata stored with the immutable receipt snapshot. */
    default String rendererVersion() {
        return "receipt-renderer-v1";
    }
}
