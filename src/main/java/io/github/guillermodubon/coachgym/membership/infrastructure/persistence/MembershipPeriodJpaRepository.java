package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MembershipPeriodJpaRepository
        extends JpaRepository<
        MembershipPeriodJpaEntity,
        UUID> {

    Optional<MembershipPeriodJpaEntity>
    findFirstByMembershipIdOrderByPeriodNumberDesc(
            UUID membershipId);

    Optional<MembershipPeriodJpaEntity>
    findByIdAndMembershipId(
            UUID id,
            UUID membershipId);
}
