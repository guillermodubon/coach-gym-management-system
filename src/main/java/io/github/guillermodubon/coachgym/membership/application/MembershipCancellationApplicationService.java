package io.github.guillermodubon.coachgym.membership.application;

import io.github.guillermodubon.coachgym.membership.MembershipCancelled;
import io.github.guillermodubon.coachgym.membership.MembershipDetails;
import io.github.guillermodubon.coachgym.membership.MembershipFreezeDetails;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.domain.MembershipCancellation;
import io.github.guillermodubon.coachgym.membership.domain.MembershipCancellationPolicy;
import io.github.guillermodubon.coachgym.membership.domain.MembershipValidationException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipCancellationApplicationService {

    private final MembershipStore membershipStore;

    private final MembershipFreezeStore freezeStore;

    private final ApplicationEventPublisher eventPublisher;

    private final Clock clock;

    public MembershipCancellationApplicationService(
            MembershipStore membershipStore,
            MembershipFreezeStore freezeStore,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {

        this.membershipStore =
                membershipStore;

        this.freezeStore =
                freezeStore;

        this.eventPublisher =
                eventPublisher;

        this.clock =
                clock;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MembershipDetails cancel(
            UUID membershipId,
            CancelMembershipCommand command,
            AuthenticatedActor actor) {

        validateMembershipId(
                membershipId);

        validateCommand(
                command);

        validateActor(
                actor);

        MembershipDetails membership =
                requireMembership(
                        membershipId);

        verifyVersion(
                membershipId,
                command.version(),
                membership.version());

        Instant occurredAt =
                clock.instant();

        LocalDate today =
                LocalDate.now(clock);

        MembershipCancellation cancellation =
                MembershipCancellationPolicy
                        .createCancellation(
                                membershipId,
                                membership.status(),
                                membership.currentPeriod(),
                                command.cancelledOn(),
                                today,
                                command.reason());

        boolean closedOpenFreeze =
                closeOpenFreezeIfRequired(
                        membership,
                        cancellation,
                        actor,
                        occurredAt);

        MembershipDetails cancelledMembership =
                membershipStore.cancel(
                        membershipId,
                        cancellation,
                        command.version(),
                        actor,
                        occurredAt);

        eventPublisher.publishEvent(
                new MembershipCancelled(
                        cancelledMembership.id(),
                        cancelledMembership.membershipCode(),
                        cancelledMembership.clientId(),
                        cancelledMembership
                                .currentPeriod()
                                .id(),
                        cancellation.cancelledOn(),
                        cancellation.reason(),
                        cancellation.previousStatus(),
                        MembershipStatus.CANCELLED,
                        closedOpenFreeze,
                        actor.id(),
                        actor.username(),
                        occurredAt));

        return cancelledMembership;
    }

    private boolean closeOpenFreezeIfRequired(
            MembershipDetails membership,
            MembershipCancellation cancellation,
            AuthenticatedActor actor,
            Instant occurredAt) {

        if (!cancellation.closesOpenFreeze()) {
            return false;
        }

        MembershipFreezeDetails openFreeze =
                freezeStore
                        .findOpenByMembershipId(
                                membership.id())
                        .orElseThrow(
                                () ->
                                        new MembershipFreezeNotFoundException(
                                                membership.id()));

        freezeStore.closeForCancellation(
                membership.id(),
                openFreeze.id(),
                cancellation.cancelledOn(),
                openFreeze.version(),
                actor,
                occurredAt);

        return true;
    }

    private MembershipDetails requireMembership(
            UUID membershipId) {

        return membershipStore
                .findById(
                        membershipId)
                .orElseThrow(
                        () ->
                                new MembershipNotFoundException(
                                        membershipId));
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

    private static void validateMembershipId(
            UUID membershipId) {

        if (membershipId == null) {
            throw new MembershipValidationException(
                    "Membership identifier must be provided.");
        }
    }

    private static void validateCommand(
            CancelMembershipCommand command) {

        if (command == null) {
            throw new MembershipValidationException(
                    "Cancel membership command "
                            + "must be provided.");
        }
    }

    private static void validateActor(
            AuthenticatedActor actor) {

        if (actor == null || actor.id() == null) {
            throw new MembershipValidationException(
                    "Authenticated actor must be provided.");
        }
    }
}