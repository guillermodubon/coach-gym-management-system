package io.github.guillermodubon.coachgym.payment.application;

/** Provider-neutral outcomes normalized after a provider request has been verified. */
public enum PaymentProviderEventType {
    CHECKOUT_COMPLETED,
    PAYMENT_FAILED,
    CHECKOUT_CANCELLED,
    CHECKOUT_EXPIRED
}
