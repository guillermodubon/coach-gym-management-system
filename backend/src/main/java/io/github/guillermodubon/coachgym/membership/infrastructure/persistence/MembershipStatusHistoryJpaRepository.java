package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MembershipStatusHistoryJpaRepository
        extends JpaRepository<
        MembershipStatusHistoryJpaEntity,
        UUID> {
}
