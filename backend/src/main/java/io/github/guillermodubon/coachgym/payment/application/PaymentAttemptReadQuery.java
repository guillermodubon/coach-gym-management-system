package io.github.guillermodubon.coachgym.payment.application;

import java.util.UUID;

/** Persistence port for a payment-attempt page in one authorized branch. */
public interface PaymentAttemptReadQuery {

    PaymentAttemptPage findPage(PaymentAttemptSearchQuery query, UUID authorizedBranchId);
}
