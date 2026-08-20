package io.github.guillermodubon.coachgym.membership.application;

import io.github.guillermodubon.coachgym.membership.MembershipDetails;
import io.github.guillermodubon.coachgym.membership.domain.MembershipCreation;
import io.github.guillermodubon.coachgym.membership.domain.MembershipRenewal;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MembershipStore {

    boolean existsCurrentByClientId(UUID clientId);

    MembershipDetails create(
            MembershipCreation creation,
            AuthenticatedActor actor,
            Instant occurredAt);

    MembershipDetails renew(
            UUID membershipId,
            MembershipRenewal renewal,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt);

    MembershipDetails freeze(
            UUID membershipId,
            UUID membershipPeriodId,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt);

    MembershipDetails reactivate(
            UUID membershipId,
            UUID membershipPeriodId,
            long expectedVersion,
            AuthenticatedActor actor,
            Instant occurredAt);

    Optional<MembershipDetails> findById(UUID membershipId);
}

