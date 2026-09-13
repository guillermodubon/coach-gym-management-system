package io.github.guillermodubon.coachgym.payment.application;

import java.util.UUID;

/** Server-owned canonical key format shared by persistence and storage adapters. */
public final class PaymentReceiptStorageKey {

    private PaymentReceiptStorageKey() {
    }

    public static String forReceipt(UUID receiptId) {
        if (receiptId == null) {
            throw new PaymentReceiptValidationException("Receipt id is required for storage.");
        }
        return "receipts/" + receiptId + ".pdf";
    }
}
