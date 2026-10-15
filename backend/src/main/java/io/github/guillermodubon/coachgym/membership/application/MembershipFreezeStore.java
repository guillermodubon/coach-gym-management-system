package io.github.guillermodubon.coachgym.membership.application;

import io.github.guillermodubon.coachgym.membership.MembershipFreezeDetails;
import io.github.guillermodubon.coachgym.membership.domain.MembershipFreeze;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface MembershipFreezeStore {

    Optional<MembershipFreezeDetails>
    findOpenByMembershipId(
            UUID membershipId);

    boolean hasOpenFreeze(
            UUID membershipId);

    MembershipFreezeDetails create(
            MembershipFreeze freeze,
            AuthenticatedActor actor,
            Instant occurredAt);

    MembershipFreezeDetails reactivate(
            UUID membershipId,
            UUID freezeId,
            LocalDate reactivatedOn,
            long expectedFreezeVersion,
            AuthenticatedActor actor,
            Instant occurredAt);

    MembershipFreezeDetails closeForCancellation(
            UUID membershipId,
            UUID freezeId,
            LocalDate cancelledOn,
            long expectedFreezeVersion,
            AuthenticatedActor actor,
            Instant occurredAt);
}