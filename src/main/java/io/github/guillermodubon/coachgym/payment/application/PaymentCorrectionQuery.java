package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentCorrectionDetails;
import java.util.Optional;
import java.util.UUID;

/** Read port for the current correction associated with a payment. */
public interface PaymentCorrectionQuery {

    Optional<PaymentCorrectionDetails> findByPaymentId(UUID paymentId);

    default Optional<PaymentCorrectionDetails> findByPaymentId(
            UUID paymentId, UUID branchId) {
        return findByPaymentId(paymentId);
    }
}
