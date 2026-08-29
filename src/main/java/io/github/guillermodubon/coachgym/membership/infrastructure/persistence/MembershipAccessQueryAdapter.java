package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import io.github.guillermodubon.coachgym.membership.MembershipAccessDetails;
import io.github.guillermodubon.coachgym.membership.MembershipAccessQuery;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class MembershipAccessQueryAdapter implements MembershipAccessQuery {

    private static final EnumSet<MembershipStatus> CURRENT_STATUSES =
            EnumSet.of(MembershipStatus.ACTIVE, MembershipStatus.FROZEN);

    private final MembershipJpaRepository membershipRepository;
    private final MembershipPeriodJpaRepository periodRepository;
    private final MembershipFreezeJpaRepository freezeRepository;

    MembershipAccessQueryAdapter(
            MembershipJpaRepository membershipRepository,
            MembershipPeriodJpaRepository periodRepository,
            MembershipFreezeJpaRepository freezeRepository) {

        this.membershipRepository = membershipRepository;
        this.periodRepository = periodRepository;
        this.freezeRepository = freezeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MembershipAccessDetails> findByCode(String normalizedCode) {
        return membershipRepository
                .findByMembershipCodeIgnoreCase(normalizedCode)
                .flatMap(this::toDetails);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MembershipAccessDetails> findCurrentByClientId(UUID clientId) {
        return membershipRepository
                .findFirstByClientIdAndStatusIn(clientId, CURRENT_STATUSES)
                .flatMap(this::toDetails);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Assembles the full {@link MembershipAccessDetails} from the membership
     * entity by making two additional targeted reads: the latest period and
     * the open freeze (if any).
     *
     * <p>Returns empty if no current period is found — this signals a data
     * inconsistency that the application service must surface as an
     * {@link IllegalStateException}, not as an empty Optional that would be
     * misread as "membership not found".</p>
     */
    private Optional<MembershipAccessDetails> toDetails(
            MembershipJpaEntity membership) {

        return periodRepository
                .findFirstByMembershipIdOrderByPeriodNumberDesc(membership.id())
                .map(period -> {

                    // Freeze is optional — absent when no open freeze exists.
                    LocalDate freezeStartsOn = null;
                    LocalDate freezePlannedEndsOn = null;

                    Optional<MembershipFreezeJpaEntity> openFreeze =
                            freezeRepository
                                    .findFirstByMembershipIdAndReactivatedOnIsNullAndCancelledOnIsNull(
                                            membership.id());

                    if (openFreeze.isPresent()) {
                        MembershipFreezeJpaEntity freeze = openFreeze.get();
                        freezeStartsOn = freeze.startsOn();
                        freezePlannedEndsOn = freeze.plannedEndsOn();
                    }

                    return new MembershipAccessDetails(
                            membership.id(),
                            membership.membershipCode(),
                            membership.clientId(),
                            membership.status(),
                            period.id(),
                            period.startsOn(),
                            period.effectiveEndsOn(),
                            freezeStartsOn,
                            freezePlannedEndsOn);
                });
    }
}
