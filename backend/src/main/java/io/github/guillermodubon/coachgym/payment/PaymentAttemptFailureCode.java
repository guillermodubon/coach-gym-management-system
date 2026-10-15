package io.github.guillermodubon.coachgym.payment;

/** Safe, provider-neutral failure category exposed by a payment attempt. */
public enum PaymentAttemptFailureCode {
    PROVIDER_DECLINED,
    PROVIDER_UNAVAILABLE,
    PROVIDER_DATA_MISMATCH,
    PROVIDER_CANCELLED,
    PROVIDER_EXPIRED
}
