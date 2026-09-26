package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentCorrectionDetails;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.UUID;

/** Write port for atomic payment void and full-refund operations. */
public interface PaymentCorrectionStore {

    PaymentCorrectionDetails voidPayment(
            VoidPaymentCommand command,
            AuthenticatedActor actor,
            Instant occurredAt);

    default PaymentCorrectionDetails voidPayment(
            VoidPaymentCommand command,
            AuthenticatedActor actor,
            Instant occurredAt,
            UUID branchId) {
        return voidPayment(command, actor, occurredAt);
    }

    PaymentCorrectionDetails refundPayment(
            RefundPaymentCommand command,
            AuthenticatedActor actor,
            Instant occurredAt);

    default PaymentCorrectionDetails refundPayment(
            RefundPaymentCommand command,
            AuthenticatedActor actor,
            Instant occurredAt,
            UUID branchId) {
        return refundPayment(command, actor, occurredAt);
    }
}
