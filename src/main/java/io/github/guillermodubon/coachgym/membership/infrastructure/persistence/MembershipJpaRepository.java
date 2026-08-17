package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MembershipJpaRepository
        extends JpaRepository<
        MembershipJpaEntity,
        UUID> {

    boolean existsByClientIdAndStatusIn(
            UUID clientId,
            Collection<MembershipStatus> statuses);
}
