package io.github.guillermodubon.coachgym.membership.domain;

import io.github.guillermodubon.coachgym.plan.DurationUnit;
import java.time.LocalDate;

public final class MembershipPeriodPolicy {

    private MembershipPeriodPolicy() {
    }

    public static MembershipPeriodDates calculate(
            LocalDate startsOn,
            int durationValue,
            DurationUnit durationUnit) {

        if (startsOn == null) {
            throw new MembershipValidationException(
                    "Membership period start date must be provided.");
        }

        if (durationValue <= 0) {
            throw new MembershipValidationException(
                    "Membership period duration must be positive.");
        }

        if (durationUnit == null) {
            throw new MembershipValidationException(
                    "Membership period duration unit "
                            + "must be provided.");
        }

        LocalDate baseEndsOn =
                addDuration(
                        startsOn,
                        durationValue,
                        durationUnit);

        return new MembershipPeriodDates(
                startsOn,
                baseEndsOn,
                baseEndsOn);
    }

    private static LocalDate addDuration(
            LocalDate startsOn,
            int durationValue,
            DurationUnit durationUnit) {

        return switch (durationUnit) {
            case DAY ->
                    startsOn.plusDays(durationValue);
            case WEEK ->
                    startsOn.plusWeeks(durationValue);
            case MONTH ->
                    startsOn.plusMonths(durationValue);
            case YEAR ->
                    startsOn.plusYears(durationValue);
        };
    }
}
