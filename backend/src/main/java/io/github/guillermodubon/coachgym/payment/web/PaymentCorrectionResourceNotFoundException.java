package io.github.guillermodubon.coachgym.payment.web;

import java.util.UUID;

final class PaymentCorrectionResourceNotFoundException
        extends RuntimeException {

    PaymentCorrectionResourceNotFoundException(UUID paymentId) {
        super("No correction exists for the requested payment.");
    }
}
