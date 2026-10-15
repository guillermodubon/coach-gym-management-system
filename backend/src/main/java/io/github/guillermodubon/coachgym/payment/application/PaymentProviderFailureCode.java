package io.github.guillermodubon.coachgym.payment.application;

/** Stable categories for failures at an external payment-provider boundary. */
public enum PaymentProviderFailureCode {
    UNAVAILABLE,
    TIMEOUT,
    INVALID_RESPONSE,
    INVALID_SIGNATURE,
    UNSUPPORTED_EVENT
}
