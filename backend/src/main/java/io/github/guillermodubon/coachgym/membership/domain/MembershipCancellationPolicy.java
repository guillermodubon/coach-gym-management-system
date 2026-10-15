package io.github.guillermodubon.coachgym.membership.domain;

import io.github.guillermodubon.coachgym.membership.MembershipPeriodDetails;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.application.MembershipAlreadyCancelledException;
import io.github.guillermodubon.coachgym.membership.application.MembershipCancellationStateConflictException;
import java.time.LocalDate;
import java.util.UUID;

public final class MembershipCancellationPolicy {

    private MembershipCancellationPolicy() {
    }

    public static MembershipCancellation createCancellation(
            UUID membershipId,
            MembershipStatus currentStatus,
            MembershipPeriodDetails currentPeriod,
            LocalDate cancelledOn,
            LocalDate today,
            String reason) {

        validateMembershipId(
                membershipId);

        validateCurrentStatus(
                currentStatus);

        validateCurrentPeriod(
                currentPeriod);

        ensureCanCancel(
                membershipId,
                currentStatus);

        validateCancellationDate(
                cancelledOn,
                today,
                currentPeriod);

        return new MembershipCancellation(
                membershipId,
                currentPeriod.id(),
                cancelledOn,
                reason,
                currentStatus);
    }

    private static void ensureCanCancel(
            UUID membershipId,
            MembershipStatus currentStatus) {

        if (currentStatus
                == MembershipStatus.CANCELLED) {

            throw new MembershipAlreadyCancelledException(
                    membershipId);
        }

        if (currentStatus != MembershipStatus.ACTIVE
                && currentStatus != MembershipStatus.FROZEN) {

            throw new MembershipCancellationStateConflictException(
                    membershipId,
                    currentStatus);
        }
    }

    private static void validateCancellationDate(
            LocalDate cancelledOn,
            LocalDate today,
            MembershipPeriodDetails currentPeriod) {

        if (cancelledOn == null) {
            throw new MembershipValidationException(
                    "Membership cancellation date must be provided.");
        }

        if (today == null) {
            throw new MembershipValidationException(
                    "Current operational date must be provided.");
        }

        if (cancelledOn.isAfter(today)) {
            throw new MembershipValidationException(
                    "Membership cancellation date must not be "
                            + "after the current operational date.");
        }

        if (cancelledOn.isBefore(
                currentPeriod.startsOn())) {

            throw new MembershipValidationException(
                    "Membership cancellation date must not be "
                            + "before the current period start date.");
        }

        if (cancelledOn.isAfter(
                currentPeriod.effectiveEndsOn())) {

            throw new MembershipValidationException(
                    "Membership cancellation date must not be "
                            + "after the current period end date.");
        }
    }

    private static void validateMembershipId(
            UUID membershipId) {

        if (membershipId == null) {
            throw new MembershipValidationException(
                    "Membership identifier must be provided.");
        }
    }

    private static void validateCurrentStatus(
            MembershipStatus currentStatus) {

        if (currentStatus == null) {
            throw new MembershipValidationException(
                    "Membership status must be provided.");
        }
    }

    private static void validateCurrentPeriod(
            MembershipPeriodDetails currentPeriod) {

        if (currentPeriod == null) {
            throw new MembershipValidationException(
                    "Current membership period must be provided.");
        }

        if (currentPeriod.id() == null) {
            throw new MembershipValidationException(
                    "Current membership period identifier "
                            + "must be provided.");
        }

        if (currentPeriod.startsOn() == null
                || currentPeriod.effectiveEndsOn() == null) {

            throw new MembershipValidationException(
                    "Current membership period dates "
                            + "must be provided.");
        }
    }
}
