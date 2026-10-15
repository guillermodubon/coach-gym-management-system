package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentAttemptDetails;
import java.util.Optional;
import java.util.UUID;

/** Durable boundary for payment-attempt state and its initial history record. */
public interface PaymentAttemptStore {

    PaymentAttemptDetails create(PersistPaymentAttemptCommand command);

    Optional<PaymentAttemptDetails> findById(UUID paymentAttemptId);

    default Optional<PaymentAttemptDetails> findById(
            UUID paymentAttemptId, UUID branchId) {
        return findById(paymentAttemptId);
    }

    Optional<PaymentAttemptProviderDetails> findProviderDetails(UUID paymentAttemptId);

    default Optional<PaymentAttemptProviderDetails> findProviderDetails(
            UUID paymentAttemptId, UUID branchId) {
        return findProviderDetails(paymentAttemptId);
    }

    PaymentAttemptDetails markProcessing(ProcessPaymentAttemptCommand command);

    PaymentAttemptDetails markFailed(FailPaymentAttemptCommand command);

    PaymentAttemptDetails markCancelled(CancelPaymentAttemptPersistenceCommand command);

    PaymentAttemptDetails markProviderSucceeded(ConfirmProviderPaymentAttemptCommand command);

    PaymentAttemptDetails markProviderFailure(ProviderPaymentFailureCommand command);
}
