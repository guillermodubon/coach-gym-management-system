package io.github.guillermodubon.coachgym.membership.domain;

import io.github.guillermodubon.coachgym.membership.MembershipPeriodDetails;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.application.MembershipNotRenewableException;
import io.github.guillermodubon.coachgym.plan.DurationUnit;
import java.time.LocalDate;
import java.util.UUID;

public final class MembershipRenewalPolicy {

    private MembershipRenewalPolicy() {
    }

    public static RenewalDecision evaluate(
            UUID membershipId,
            MembershipStatus status,
            MembershipPeriodDetails currentPeriod,
            LocalDate requestedStartsOn,
            LocalDate today,
            int durationValue,
            DurationUnit durationUnit) {

        validateInputs(
                membershipId,
                status,
                currentPeriod,
                today);

        ensureRenewable(
                membershipId,
                status);

        short nextPeriodNumber =
                nextPeriodNumber(
                        currentPeriod.periodNumber());

        LocalDate startsOn =
                determineStartsOn(
                        status,
                        currentPeriod,
                        requestedStartsOn,
                        today);

        MembershipPeriodDates dates =
                MembershipPeriodPolicy.calculate(
                        startsOn,
                        durationValue,
                        durationUnit);

        return new RenewalDecision(
                nextPeriodNumber,
                status,
                MembershipStatus.ACTIVE,
                dates);
    }

    private static void ensureRenewable(
            UUID membershipId,
            MembershipStatus status) {

        if (status == MembershipStatus.FROZEN
                || status == MembershipStatus.CANCELLED) {

            throw new MembershipNotRenewableException(
                    membershipId,
                    status);
        }
    }

    private static LocalDate determineStartsOn(
            MembershipStatus status,
            MembershipPeriodDetails currentPeriod,
            LocalDate requestedStartsOn,
            LocalDate today) {

        if (status == MembershipStatus.ACTIVE) {
            return currentPeriod.effectiveEndsOn();
        }

        if (requestedStartsOn == null) {
            throw new MembershipValidationException(
                    "Renewal start date must be provided "
                            + "for an expired membership.");
        }

        if (requestedStartsOn.isBefore(today)) {
            throw new MembershipValidationException(
                    "Renewal start date must not be before "
                            + "the current operational date.");
        }

        return requestedStartsOn;
    }

    private static short nextPeriodNumber(
            short currentPeriodNumber) {

        if (currentPeriodNumber <= 0) {
            throw new MembershipValidationException(
                    "Current membership period number must be positive.");
        }

        if (currentPeriodNumber == Short.MAX_VALUE) {
            throw new MembershipValidationException(
                    "Membership period number has reached "
                            + "its supported limit.");
        }

        return (short) (currentPeriodNumber + 1);
    }

    private static void validateInputs(
            UUID membershipId,
            MembershipStatus status,
            MembershipPeriodDetails currentPeriod,
            LocalDate today) {

        if (membershipId == null) {
            throw new MembershipValidationException(
                    "Membership identifier must be provided.");
        }

        if (status == null) {
            throw new MembershipValidationException(
                    "Membership status must be provided.");
        }

        if (currentPeriod == null) {
            throw new MembershipValidationException(
                    "Current membership period must be provided.");
        }

        if (today == null) {
            throw new MembershipValidationException(
                    "Current operational date must be provided.");
        }
    }

    public record RenewalDecision(
            short periodNumber,
            MembershipStatus previousStatus,
            MembershipStatus resultingStatus,
            MembershipPeriodDates dates) {

        public RenewalDecision {

            if (periodNumber <= 1) {
                throw new MembershipValidationException(
                        "Renewal period number must be greater than one.");
            }

            if (previousStatus == null) {
                throw new MembershipValidationException(
                        "Previous membership status must be provided.");
            }

            if (resultingStatus != MembershipStatus.ACTIVE) {
                throw new MembershipValidationException(
                        "Renewal resulting status must be ACTIVE.");
            }

            if (dates == null) {
                throw new MembershipValidationException(
                        "Renewal period dates must be provided.");
            }
        }

        public boolean changesMembershipStatus() {
            return previousStatus != resultingStatus;
        }
    }
}