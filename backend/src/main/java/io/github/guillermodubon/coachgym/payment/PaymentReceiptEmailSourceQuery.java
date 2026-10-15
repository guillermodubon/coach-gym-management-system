package io.github.guillermodubon.coachgym.payment;

import java.util.Optional;
import java.util.UUID;

/**
 * Public payment boundary for delivery of an existing canonical receipt.
 * Receipt generation and payment infrastructure remain private to payment.
 */
public interface PaymentReceiptEmailSourceQuery {

    Optional<PaymentReceiptEmailSource> findByReceiptId(UUID receiptId);

    Optional<PaymentReceiptEmailSource> findByPaymentId(UUID paymentId);
}
