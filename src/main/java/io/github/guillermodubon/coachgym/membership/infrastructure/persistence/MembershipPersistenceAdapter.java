package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import io.github.guillermodubon.coachgym.membership.MembershipDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodDetails;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.application.CurrentMembershipAlreadyExistsException;
import io.github.guillermodubon.coachgym.membership.application.MembershipStore;
import io.github.guillermodubon.coachgym.membership.domain.MembershipCreation;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class MembershipPersistenceAdapter
        implements MembershipStore {

    private static final EnumSet<MembershipStatus>
            CURRENT_STATUSES =
            EnumSet.of(
                    MembershipStatus.ACTIVE,
                    MembershipStatus.FROZEN);

    private static final String CURRENT_MEMBERSHIP_CONSTRAINT =
            "uq_memberships_one_current_per_client";

    private final MembershipJpaRepository
            membershipRepository;

    private final MembershipPeriodJpaRepository
            periodRepository;

    private final MembershipStatusHistoryJpaRepository
            statusHistoryRepository;

    private final EntityManager entityManager;

    MembershipPersistenceAdapter(
            MembershipJpaRepository membershipRepository,
            MembershipPeriodJpaRepository periodRepository,
            MembershipStatusHistoryJpaRepository
                    statusHistoryRepository,
            EntityManager entityManager) {

        this.membershipRepository =
                membershipRepository;

        this.periodRepository =
                periodRepository;

        this.statusHistoryRepository =
                statusHistoryRepository;

        this.entityManager =
                entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsCurrentByClientId(
            UUID clientId) {

        return membershipRepository
                .existsByClientIdAndStatusIn(
                        clientId,
                        CURRENT_STATUSES);
    }

    @Override
    @Transactional
    public MembershipDetails create(
            MembershipCreation creation,
            AuthenticatedActor actor,
            Instant occurredAt) {

        try {
            MembershipJpaEntity membership =
                    membershipRepository.saveAndFlush(
                            MembershipJpaEntity.create(
                                    creation.clientId(),
                                    actor,
                                    occurredAt));

            entityManager.refresh(
                    membership);

            MembershipPeriodJpaEntity period =
                    periodRepository.saveAndFlush(
                            MembershipPeriodJpaEntity.initial(
                                    membership.id(),
                                    creation,
                                    actor,
                                    occurredAt));

            entityManager.refresh(
                    period);

            statusHistoryRepository.saveAndFlush(
                    MembershipStatusHistoryJpaEntity
                            .initialActivation(
                                    membership.id(),
                                    period.id(),
                                    actor,
                                    occurredAt));

            return toDetails(
                    membership,
                    period);
        } catch (DataIntegrityViolationException exception) {
            if (isCurrentMembershipConflict(
                    exception)) {

                throw new CurrentMembershipAlreadyExistsException(
                        creation.clientId());
            }

            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MembershipDetails> findById(
            UUID membershipId) {

        return membershipRepository.findById(
                        membershipId)
                .flatMap(
                        membership ->
                                periodRepository
                                        .findFirstByMembershipIdOrderByPeriodNumberDesc(
                                                membership.id())
                                        .map(
                                                period ->
                                                        toDetails(
                                                                membership,
                                                                period)));
    }

    private static MembershipDetails toDetails(
            MembershipJpaEntity membership,
            MembershipPeriodJpaEntity period) {

        MembershipPeriodDetails periodDetails =
                period.toDetails();

        return new MembershipDetails(
                membership.id(),
                membership.membershipCode(),
                membership.clientId(),
                membership.status(),
                periodDetails,
                membership.createdAt(),
                membership.updatedAt(),
                membership.version());
    }

    private static boolean isCurrentMembershipConflict(
            Throwable exception) {

        Throwable current =
                exception;

        while (current != null) {
            String message =
                    current.getMessage();

            if (message != null
                    && message.contains(
                    CURRENT_MEMBERSHIP_CONSTRAINT)) {

                return true;
            }

            if (current instanceof SQLException sqlException
                    && "23505".equals(
                    sqlException.getSQLState())
                    && message != null
                    && message.contains(
                    CURRENT_MEMBERSHIP_CONSTRAINT)) {

                return true;
            }

            current =
                    current.getCause();
        }

        return false;
    }
}
