package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentDetails;
import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PaymentStore {

    PaymentDetails register(
            UUID clientId,
            UUID membershipId,
            UUID membershipPeriodId,
            BigDecimal amount,
            String currency,
            PaymentMethod paymentMethod,
            String externalReference,
            Instant paidAt,
            AuthenticatedActor actor,
            Instant occurredAt);

    default PaymentDetails register(
            UUID clientId,
            UUID membershipId,
            UUID membershipPeriodId,
            BigDecimal amount,
            String currency,
            PaymentMethod paymentMethod,
            String externalReference,
            Instant paidAt,
            AuthenticatedActor actor,
            Instant occurredAt,
            UUID registeredAtBranchId) {
        return register(clientId, membershipId, membershipPeriodId, amount, currency,
                paymentMethod, externalReference, paidAt, actor, occurredAt);
    }

    Optional<PaymentDetails> findById(UUID paymentId);

    default Optional<PaymentDetails> findById(UUID paymentId, UUID branchId) {
        return findById(paymentId);
    }

    boolean existsByMethodAndExternalReference(
            PaymentMethod paymentMethod,
            String externalReference);

    PaymentPage findAll(PaymentSearchQuery query);

    default PaymentPage findAll(PaymentSearchQuery query, UUID branchId) {
        return findAll(query);
    }
}
