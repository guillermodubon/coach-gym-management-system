package io.github.guillermodubon.coachgym.membership.application;

import io.github.guillermodubon.coachgym.client.ClientDetails;
import io.github.guillermodubon.coachgym.client.ClientQuery;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.membership.*;
import io.github.guillermodubon.coachgym.membership.domain.MembershipFreeze;
import io.github.guillermodubon.coachgym.membership.domain.MembershipFreezePolicy;
import io.github.guillermodubon.coachgym.membership.domain.MembershipValidationException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipFreezeApplicationService {

    private final MembershipStore membershipStore;
    private final MembershipFreezeStore freezeStore;
    private final ClientQuery clientQuery;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public MembershipFreezeApplicationService(
            MembershipStore membershipStore,
            MembershipFreezeStore freezeStore,
            ClientQuery clientQuery,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {

        this.membershipStore = membershipStore;
        this.freezeStore = freezeStore;
        this.clientQuery = clientQuery;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public MembershipDetails freeze(
            UUID membershipId,
            FreezeMembershipCommand command,
            AuthenticatedActor actor) {

        validateMembershipId(membershipId);
        validateFreezeCommand(command);
        validateActor(actor);

        MembershipDetails membership =
                requireMembership(membershipId);

        verifyVersion(
                membershipId,
                command.version(),
                membership.version());

        boolean hasOpenFreeze =
                freezeStore.hasOpenFreeze(
                        membershipId);

        MembershipFreeze freeze =
                MembershipFreezePolicy.createFreeze(
                        membershipId,
                        membership.currentPeriod().id(),
                        membership.status(),
                        hasOpenFreeze,
                        command.startsOn(),
                        command.plannedEndsOn(),
                        command.reason());

        validateFreezeDateAgainstCurrentPeriod(
                membership,
                command.startsOn());

        Instant occurredAt =
                clock.instant();

        MembershipFreezeDetails persistedFreeze =
                freezeStore.create(
                        freeze,
                        actor,
                        occurredAt);

        MembershipDetails frozenMembership =
                membershipStore.freeze(
                        membershipId,
                        membership.currentPeriod().id(),
                        command.version(),
                        actor,
                        occurredAt);

        eventPublisher.publishEvent(
                new MembershipFrozen(
                        frozenMembership.id(),
                        frozenMembership.membershipCode(),
                        frozenMembership.clientId(),
                        frozenMembership.currentPeriod().id(),
                        persistedFreeze.startsOn(),
                        persistedFreeze.plannedEndsOn(),
                        persistedFreeze.reason(),
                        MembershipStatus.ACTIVE,
                        MembershipStatus.FROZEN,
                        actor.id(),
                        actor.username(),
                        occurredAt));

        return frozenMembership;
    }

    @Transactional
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public MembershipDetails reactivate(
            UUID membershipId,
            ReactivateMembershipCommand command,
            AuthenticatedActor actor) {

        validateMembershipId(membershipId);
        validateReactivationCommand(command);
        validateActor(actor);

        MembershipDetails membership =
                requireMembership(membershipId);

        verifyVersion(
                membershipId,
                command.version(),
                membership.version());

        ensureClientIsActive(
                membership.clientId());

        MembershipFreezeDetails openFreeze =
                freezeStore
                        .findOpenByMembershipId(
                                membershipId)
                        .orElseThrow(
                                () ->
                                        new MembershipFreezeNotFoundException(
                                                membershipId));

        MembershipFreezePolicy.validateReactivation(
                membershipId,
                membership.status(),
                openFreeze,
                command.reactivatedOn());

        Instant occurredAt =
                clock.instant();

        MembershipFreezeDetails closedFreeze =
                freezeStore.reactivate(
                        membershipId,
                        openFreeze.id(),
                        command.reactivatedOn(),
                        openFreeze.version(),
                        actor,
                        occurredAt);

        MembershipDetails reactivatedMembership =
                membershipStore.reactivate(
                        membershipId,
                        membership.currentPeriod().id(),
                        command.version(),
                        actor,
                        occurredAt);

        eventPublisher.publishEvent(
                new MembershipReactivated(
                        reactivatedMembership.id(),
                        reactivatedMembership.membershipCode(),
                        reactivatedMembership.clientId(),
                        reactivatedMembership.currentPeriod().id(),
                        closedFreeze.id(),
                        closedFreeze.startsOn(),
                        closedFreeze.plannedEndsOn(),
                        closedFreeze.reactivatedOn(),
                        closedFreeze.reason(),
                        MembershipStatus.FROZEN,
                        MembershipStatus.ACTIVE,
                        actor.id(),
                        actor.username(),
                        occurredAt));

        return reactivatedMembership;
    }

    private MembershipDetails requireMembership(
            UUID membershipId) {

        return membershipStore.findById(
                        membershipId)
                .orElseThrow(
                        () -> new MembershipNotFoundException(
                                membershipId));
    }

    private void ensureClientIsActive(
            UUID clientId) {

        ClientDetails client =
                clientQuery.findClientById(clientId)
                        .orElseThrow(
                                () ->
                                        new MembershipClientNotFoundException(
                                                clientId));

        if (client.status() != ClientStatus.ACTIVE) {
            throw new InactiveMembershipClientException(
                    clientId,
                    "Client "
                            + clientId
                            + " is inactive and its membership "
                            + "cannot be reactivated.");
        }
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

    private static void validateFreezeDateAgainstCurrentPeriod(
            MembershipDetails membership,
            LocalDate startsOn) {

        if (startsOn.isBefore(
                membership.currentPeriod().startsOn())) {

            throw new MembershipValidationException(
                    "Membership freeze start date must not be "
                            + "before the current period start date.");
        }

        if (!startsOn.isBefore(
                membership.currentPeriod()
                        .effectiveEndsOn())) {

            throw new MembershipValidationException(
                    "Membership freeze start date must be "
                            + "before the current period end date.");
        }
    }

    private static void validateMembershipId(
            UUID membershipId) {

        if (membershipId == null) {
            throw new MembershipValidationException(
                    "Membership identifier must be provided.");
        }
    }

    private static void validateFreezeCommand(
            FreezeMembershipCommand command) {

        if (command == null) {
            throw new MembershipValidationException(
                    "Freeze membership command must be provided.");
        }
    }

    private static void validateReactivationCommand(
            ReactivateMembershipCommand command) {

        if (command == null) {
            throw new MembershipValidationException(
                    "Reactivate membership command must be provided.");
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
