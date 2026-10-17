package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import io.github.guillermodubon.coachgym.membership.MembershipFreezeDetails;
import io.github.guillermodubon.coachgym.membership.application.MembershipFreezeNotFoundException;
import io.github.guillermodubon.coachgym.membership.application.MembershipFreezeStore;
import io.github.guillermodubon.coachgym.membership.application.MembershipVersionConflictException;
import io.github.guillermodubon.coachgym.membership.domain.MembershipFreeze;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class MembershipFreezePersistenceAdapter
        implements MembershipFreezeStore {

    private final MembershipFreezeJpaRepository
            freezeRepository;

    MembershipFreezePersistenceAdapter(
            MembershipFreezeJpaRepository
                    freezeRepository) {

        this.freezeRepository = freezeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MembershipFreezeDetails>
    findOpenByMembershipId(
            UUID membershipId) {

        return freezeRepository
                .findFirstByMembershipIdAndReactivatedOnIsNullAndCancelledOnIsNull(
                        membershipId)
                .map(MembershipFreezeJpaEntity::toDetails);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOpenFreeze(
            UUID membershipId) {

        return freezeRepository
                .existsByMembershipIdAndReactivatedOnIsNullAndCancelledOnIsNull(
                        membershipId);
    }

    @Override
    @Transactional
    public MembershipFreezeDetails create(
            MembershipFreeze freeze,
            AuthenticatedActor actor,
            Instant occurredAt) {

        MembershipFreezeJpaEntity entity =
                MembershipFreezeJpaEntity.create(
                        freeze,
                        actor,
                        occurredAt);

        MembershipFreezeJpaEntity saved =
                freezeRepository.saveAndFlush(entity);

        return saved.toDetails();
    }

    @Override
    @Transactional
    public MembershipFreezeDetails reactivate(
            UUID membershipId,
            UUID freezeId,
            LocalDate reactivatedOn,
            long expectedFreezeVersion,
            AuthenticatedActor actor,
            Instant occurredAt) {

        MembershipFreezeJpaEntity freeze =
                freezeRepository.findById(freezeId)
                        .orElseThrow(
                                () ->
                                        new MembershipFreezeNotFoundException(
                                                membershipId));

        if (!membershipId.equals(
                freeze.membershipId())) {

            throw new MembershipFreezeNotFoundException(
                    membershipId);
        }

        verifyVersion(
                membershipId,
                expectedFreezeVersion,
                freeze.version());

        if (!freeze.open()) {
            throw new MembershipFreezeNotFoundException(
                    membershipId);
        }

        freeze.reactivate(
                reactivatedOn,
                actor,
                occurredAt);

        MembershipFreezeJpaEntity saved =
                freezeRepository.saveAndFlush(freeze);

        return saved.toDetails();
    }

    private static void verifyVersion(
            UUID membershipId,
            long expectedVersion,
            long currentVersion) {

        if (expectedVersion != currentVersion) {
            throw new MembershipVersionConflictException(
                    membershipId,
                    expectedVersion,
                    currentVersion);
        }
    }

    @Override
    @Transactional
    public MembershipFreezeDetails closeForCancellation(
            UUID membershipId,
            UUID freezeId,
            LocalDate cancelledOn,
            long expectedFreezeVersion,
            AuthenticatedActor actor,
            Instant occurredAt) {

        MembershipFreezeJpaEntity freeze =
                freezeRepository.findById(
                                freezeId)
                        .orElseThrow(
                                () ->
                                        new MembershipFreezeNotFoundException(
                                                membershipId));

        if (!membershipId.equals(
                freeze.membershipId())) {

            throw new MembershipFreezeNotFoundException(
                    membershipId);
        }

        verifyVersion(
                membershipId,
                expectedFreezeVersion,
                freeze.version());

        if (!freeze.open()) {
            throw new MembershipFreezeNotFoundException(
                    membershipId);
        }

        freeze.closeForCancellation(
                cancelledOn,
                actor,
                occurredAt);

        MembershipFreezeJpaEntity saved =
                freezeRepository.saveAndFlush(
                        freeze);

        return saved.toDetails();
    }
}
