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

    Optional<PaymentDetails> findById(UUID paymentId);

    boolean existsByMethodAndExternalReference(
            PaymentMethod paymentMethod,
            String externalReference);

    PaymentPage findAll(PaymentSearchQuery query);
}
