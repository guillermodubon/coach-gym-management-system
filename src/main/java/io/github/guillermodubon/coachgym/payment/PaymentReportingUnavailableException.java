package io.github.guillermodubon.coachgym.payment;

/** Safe failure for an unavailable payment reporting read. */
public final class PaymentReportingUnavailableException extends RuntimeException {

    public PaymentReportingUnavailableException(Throwable cause) {
        super("Payment reporting data could not be read.", cause);
    }
}
