package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentDetails;

/** Dedicated persistence boundary for payments confirmed by a verified provider event. */
public interface ProviderConfirmedPaymentStore {

    PaymentDetails register(ProviderConfirmedPaymentCommand command);
}
