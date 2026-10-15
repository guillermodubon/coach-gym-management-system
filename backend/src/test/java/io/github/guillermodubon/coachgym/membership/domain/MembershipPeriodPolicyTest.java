package io.github.guillermodubon.coachgym.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.plan.DurationUnit;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MembershipPeriodPolicyTest {

    private static final LocalDate STARTS_ON =
            LocalDate.of(2026, 9, 1);

    @Test
    void calculatesDayDuration() {
        MembershipPeriodDates dates =
                MembershipPeriodPolicy.calculate(
                        STARTS_ON,
                        1,
                        DurationUnit.DAY);

        assertThat(dates.startsOn())
                .isEqualTo(STARTS_ON);

        assertThat(dates.baseEndsOn())
                .isEqualTo(
                        LocalDate.of(2026, 9, 2));

        assertThat(dates.effectiveEndsOn())
                .isEqualTo(
                        LocalDate.of(2026, 9, 2));
    }

    @Test
    void calculatesWeekDuration() {
        MembershipPeriodDates dates =
                MembershipPeriodPolicy.calculate(
                        STARTS_ON,
                        2,
                        DurationUnit.WEEK);

        assertThat(dates.baseEndsOn())
                .isEqualTo(
                        LocalDate.of(2026, 9, 15));
    }

    @Test
    void calculatesMonthDuration() {
        MembershipPeriodDates dates =
                MembershipPeriodPolicy.calculate(
                        STARTS_ON,
                        1,
                        DurationUnit.MONTH);

        assertThat(dates.baseEndsOn())
                .isEqualTo(
                        LocalDate.of(2026, 10, 1));
    }

    @Test
    void calculatesYearDuration() {
        MembershipPeriodDates dates =
                MembershipPeriodPolicy.calculate(
                        STARTS_ON,
                        1,
                        DurationUnit.YEAR);

        assertThat(dates.baseEndsOn())
                .isEqualTo(
                        LocalDate.of(2027, 9, 1));
    }

    @Test
    void usesJavaCalendarSemanticsForEndOfMonth() {
        MembershipPeriodDates dates =
                MembershipPeriodPolicy.calculate(
                        LocalDate.of(2026, 1, 31),
                        1,
                        DurationUnit.MONTH);

        assertThat(dates.baseEndsOn())
                .isEqualTo(
                        LocalDate.of(2026, 2, 28));
    }

    @Test
    void rejectsMissingStartDate() {
        assertThatThrownBy(
                () ->
                        MembershipPeriodPolicy.calculate(
                                null,
                                1,
                                DurationUnit.MONTH))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership period start date "
                                + "must be provided.");
    }

    @Test
    void rejectsNonPositiveDuration() {
        assertThatThrownBy(
                () ->
                        MembershipPeriodPolicy.calculate(
                                STARTS_ON,
                                0,
                                DurationUnit.MONTH))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership period duration "
                                + "must be positive.");
    }

    @Test
    void rejectsMissingDurationUnit() {
        assertThatThrownBy(
                () ->
                        MembershipPeriodPolicy.calculate(
                                STARTS_ON,
                                1,
                                null))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership period duration unit "
                                + "must be provided.");
    }
}