package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MembershipJpaRepository
        extends JpaRepository<
        MembershipJpaEntity,
        UUID> {

    boolean existsByClientIdAndStatusIn(
            UUID clientId,
            Collection<MembershipStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select membership
        from MembershipJpaEntity membership
        where membership.id = :membershipId
        """)
    Optional<MembershipJpaEntity> findByIdForUpdate(
            @Param("membershipId")
            UUID membershipId);
}
