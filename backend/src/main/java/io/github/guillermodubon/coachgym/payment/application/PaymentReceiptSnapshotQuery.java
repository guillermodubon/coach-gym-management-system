package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentReceiptSourceSnapshot;
import java.util.Optional;
import java.util.UUID;

/** Read port for authoritative payment, client, membership, and pricing data. */
public interface PaymentReceiptSnapshotQuery {

    Optional<PaymentReceiptSourceSnapshot> findByPaymentId(UUID paymentId);
}
