package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentReceiptDetails;
import java.util.Optional;
import java.util.UUID;

/** Read port for canonical receipt metadata. */
public interface PaymentReceiptQuery {

    Optional<PaymentReceiptDetails> findById(UUID receiptId);

    Optional<PaymentReceiptDetails> findByPaymentId(UUID paymentId);
}
