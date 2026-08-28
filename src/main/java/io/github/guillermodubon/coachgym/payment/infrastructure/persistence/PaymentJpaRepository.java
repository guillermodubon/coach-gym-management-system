package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface PaymentJpaRepository
        extends JpaRepository<PaymentJpaEntity, UUID>,
        JpaSpecificationExecutor<PaymentJpaEntity> {

    boolean existsByPaymentMethodAndExternalReference(
            PaymentMethod paymentMethod,
            String externalReference);
}