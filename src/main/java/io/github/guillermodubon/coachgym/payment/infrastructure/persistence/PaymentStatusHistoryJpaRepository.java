package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentStatusHistoryJpaRepository
        extends JpaRepository<PaymentStatusHistoryJpaEntity, UUID> {
}
