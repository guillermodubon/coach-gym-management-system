package io.github.guillermodubon.coachgym.payment.application;

import java.util.Objects;

/** Safe provider-boundary exception that never carries provider response text. */
public class PaymentProviderException extends RuntimeException {

    private final PaymentProviderFailureCode failureCode;

    public PaymentProviderException(PaymentProviderFailureCode failureCode) {
        super("Payment provider operation failed.");
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode");
    }

    public PaymentProviderFailureCode failureCode() {
        return failureCode;
    }
}
