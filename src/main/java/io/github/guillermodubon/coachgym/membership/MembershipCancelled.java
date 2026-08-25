package io.github.guillermodubon.coachgym.membership;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MembershipCancelled(
        UUID membershipId,
        String membershipCode,
        UUID clientId,
        UUID membershipPeriodId,
        LocalDate cancelledOn,
        String reason,
        MembershipStatus previousStatus,
        MembershipStatus resultingStatus,
        boolean closedOpenFreeze,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {

    public MembershipCancelled {
        if (membershipId == null) {
            throw new IllegalArgumentException(
                    "Membership identifier must be provided.");
        }

        if (membershipCode == null
                || membershipCode.isBlank()) {

            throw new IllegalArgumentException(
                    "Membership code must be provided.");
        }

        if (clientId == null) {
            throw new IllegalArgumentException(
                    "Membership client identifier "
                            + "must be provided.");
        }

        if (membershipPeriodId == null) {
            throw new IllegalArgumentException(
                    "Membership period identifier "
                            + "must be provided.");
        }

        if (cancelledOn == null) {
            throw new IllegalArgumentException(
                    "Membership cancellation date "
                            + "must be provided.");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Membership cancellation reason "
                            + "must not be blank.");
        }

        if (previousStatus != MembershipStatus.ACTIVE
                && previousStatus
                != MembershipStatus.FROZEN) {

            throw new IllegalArgumentException(
                    "Membership cancellation previous status "
                            + "must be ACTIVE or FROZEN.");
        }

        if (resultingStatus
                != MembershipStatus.CANCELLED) {

            throw new IllegalArgumentException(
                    "Membership cancellation resulting status "
                            + "must be CANCELLED.");
        }

        if (closedOpenFreeze
                != (previousStatus
                == MembershipStatus.FROZEN)) {

            throw new IllegalArgumentException(
                    "Closed-open-freeze indicator does not "
                            + "match the previous membership status.");
        }

        if (actorUserId == null) {
            throw new IllegalArgumentException(
                    "Actor user identifier must be provided.");
        }

        if (actorIdentifier == null
                || actorIdentifier.isBlank()) {

            throw new IllegalArgumentException(
                    "Actor identifier must be provided.");
        }

        if (occurredAt == null) {
            throw new IllegalArgumentException(
                    "Occurrence time must be provided.");
        }

        membershipCode =
                membershipCode.trim();

        reason =
                reason.trim();

        actorIdentifier =
                actorIdentifier.trim();
    }
}
