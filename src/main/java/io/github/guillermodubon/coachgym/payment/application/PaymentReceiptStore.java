package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentReceiptDetails;

/** Persistence port for immutable canonical receipt metadata. */
public interface PaymentReceiptStore {

    PaymentReceiptDetails save(PaymentReceiptDetails details);
}
