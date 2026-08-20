package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MembershipFreezeJpaRepository
        extends JpaRepository<
        MembershipFreezeJpaEntity,
        UUID> {

    Optional<MembershipFreezeJpaEntity>
    findFirstByMembershipIdAndReactivatedOnIsNull(
            UUID membershipId);

    boolean existsByMembershipIdAndReactivatedOnIsNull(
            UUID membershipId);
}
