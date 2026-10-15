package io.github.guillermodubon.coachgym.payment.application;

/** Server-side generator for receipt numbers. */
public interface PaymentReceiptNumberGenerator {

    String next();
}
