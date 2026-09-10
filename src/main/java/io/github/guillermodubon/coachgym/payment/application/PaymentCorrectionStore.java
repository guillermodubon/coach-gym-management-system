package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentCorrectionDetails;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;

/** Write port for atomic payment void and full-refund operations. */
public interface PaymentCorrectionStore {

    PaymentCorrectionDetails voidPayment(
            VoidPaymentCommand command,
            AuthenticatedActor actor,
            Instant occurredAt);

    PaymentCorrectionDetails refundPayment(
            RefundPaymentCommand command,
            AuthenticatedActor actor,
            Instant occurredAt);
}
