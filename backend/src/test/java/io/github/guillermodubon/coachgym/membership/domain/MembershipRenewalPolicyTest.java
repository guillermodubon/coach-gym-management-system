package io.github.guillermodubon.coachgym.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.membership.MembershipPeriodDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodSource;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.application.MembershipNotRenewableException;
import io.github.guillermodubon.coachgym.plan.DurationUnit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipRenewalPolicyTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "69fd470e-b8a9-4ccc-9371-c6588a300c20");

    private static final UUID PERIOD_ID =
            UUID.fromString(
                    "1a0e874d-aaec-459a-8183-8c7aa9706eb7");

    private static final UUID PLAN_ID =
            UUID.fromString(
                    "0f9e99a5-d5f0-4638-a511-62da411ad375");

    private static final LocalDate TODAY =
            LocalDate.of(2026, 10, 1);

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-01T14:00:00Z");

    @Test
    void activeMembershipRenewsFromCurrentEffectiveEnd() {
        MembershipPeriodDetails currentPeriod =
                currentPeriod(
                        (short) 1,
                        LocalDate.of(2026, 10, 15));

        MembershipRenewalPolicy.RenewalDecision decision =
                MembershipRenewalPolicy.evaluate(
                        MEMBERSHIP_ID,
                        MembershipStatus.ACTIVE,
                        currentPeriod,
                        LocalDate.of(2026, 11, 1),
                        TODAY,
                        1,
                        DurationUnit.MONTH);

        assertThat(decision.periodNumber())
                .isEqualTo((short) 2);

        assertThat(decision.previousStatus())
                .isEqualTo(MembershipStatus.ACTIVE);

        assertThat(decision.resultingStatus())
                .isEqualTo(MembershipStatus.ACTIVE);

        assertThat(decision.changesMembershipStatus())
                .isFalse();

        assertThat(decision.dates().startsOn())
                .isEqualTo(
                        LocalDate.of(2026, 10, 15));

        assertThat(decision.dates().baseEndsOn())
                .isEqualTo(
                        LocalDate.of(2026, 11, 15));

        assertThat(decision.dates().effectiveEndsOn())
                .isEqualTo(
                        LocalDate.of(2026, 11, 15));
    }

    @Test
    void expiredMembershipRenewsFromRequestedStartDate() {
        MembershipPeriodDetails currentPeriod =
                currentPeriod(
                        (short) 3,
                        LocalDate.of(2026, 9, 1));

        MembershipRenewalPolicy.RenewalDecision decision =
                MembershipRenewalPolicy.evaluate(
                        MEMBERSHIP_ID,
                        MembershipStatus.EXPIRED,
                        currentPeriod,
                        LocalDate.of(2026, 10, 5),
                        TODAY,
                        3,
                        DurationUnit.MONTH);

        assertThat(decision.periodNumber())
                .isEqualTo((short) 4);

        assertThat(decision.previousStatus())
                .isEqualTo(MembershipStatus.EXPIRED);

        assertThat(decision.resultingStatus())
                .isEqualTo(MembershipStatus.ACTIVE);

        assertThat(decision.changesMembershipStatus())
                .isTrue();

        assertThat(decision.dates().startsOn())
                .isEqualTo(
                        LocalDate.of(2026, 10, 5));

        assertThat(decision.dates().baseEndsOn())
                .isEqualTo(
                        LocalDate.of(2027, 1, 5));

        assertThat(decision.dates().effectiveEndsOn())
                .isEqualTo(
                        LocalDate.of(2027, 1, 5));
    }

    @Test
    void expiredMembershipCanRenewStartingToday() {
        MembershipRenewalPolicy.RenewalDecision decision =
                MembershipRenewalPolicy.evaluate(
                        MEMBERSHIP_ID,
                        MembershipStatus.EXPIRED,
                        currentPeriod(
                                (short) 1,
                                LocalDate.of(2026, 9, 1)),
                        TODAY,
                        TODAY,
                        1,
                        DurationUnit.MONTH);

        assertThat(decision.periodNumber())
                .isEqualTo((short) 2);

        assertThat(decision.dates().startsOn())
                .isEqualTo(TODAY);

        assertThat(decision.dates().baseEndsOn())
                .isEqualTo(
                        LocalDate.of(2026, 11, 1));

        assertThat(decision.changesMembershipStatus())
                .isTrue();
    }

    @Test
    void activeMembershipIgnoresRequestedStartDate() {
        MembershipRenewalPolicy.RenewalDecision decision =
                MembershipRenewalPolicy.evaluate(
                        MEMBERSHIP_ID,
                        MembershipStatus.ACTIVE,
                        currentPeriod(
                                (short) 1,
                                LocalDate.of(2026, 10, 15)),
                        LocalDate.of(2027, 1, 1),
                        TODAY,
                        1,
                        DurationUnit.MONTH);

        assertThat(decision.dates().startsOn())
                .isEqualTo(
                        LocalDate.of(2026, 10, 15));
    }

    @Test
    void activeMembershipAcceptsMissingRequestedStartDate() {
        MembershipRenewalPolicy.RenewalDecision decision =
                MembershipRenewalPolicy.evaluate(
                        MEMBERSHIP_ID,
                        MembershipStatus.ACTIVE,
                        currentPeriod(
                                (short) 1,
                                LocalDate.of(                                                2026,
                                        10,
                                        15)),
                        null,
                        TODAY,
                        1,
                        DurationUnit.MONTH);

        assertThat(decision.dates().startsOn())
                .isEqualTo(
                        LocalDate.of(2026, 10, 15));
    }

    @Test
    void rejectsFrozenMembership() {
        assertThatThrownBy(
                () ->
                        MembershipRenewalPolicy.evaluate(
                                MEMBERSHIP_ID,
                                MembershipStatus.FROZEN,
                                currentPeriod(
                                        (short) 1,
                                        LocalDate.of(
                                                2026,
                                                10,
                                                15)),
                                null,
                                TODAY,
                                1,
                                DurationUnit.MONTH))
                .isInstanceOf(
                        MembershipNotRenewableException.class)
                .hasMessage(
                        "Membership "
                                + MEMBERSHIP_ID
                                + " cannot be renewed while its status is "
                                + "FROZEN.");
    }

    @Test
    void rejectsCancelledMembership() {
        assertThatThrownBy(
                () ->
                        MembershipRenewalPolicy.evaluate(
                                MEMBERSHIP_ID,
                                MembershipStatus.CANCELLED,
                                currentPeriod(
                                        (short) 1,
                                        LocalDate.of(
                                                2026,
                                                9,
                                                1)),
                                TODAY,
                                TODAY,
                                1,
                                DurationUnit.MONTH))
                .isInstanceOf(
                        MembershipNotRenewableException.class)
                .hasMessage(
                        "Membership "
                                + MEMBERSHIP_ID
                                + " cannot be renewed while its status is "
                                + "CANCELLED.");
    }

    @Test
    void expiredMembershipRequiresStartDate() {
        assertThatThrownBy(
                () ->
                        MembershipRenewalPolicy.evaluate(
                                MEMBERSHIP_ID,
                                MembershipStatus.EXPIRED,
                                currentPeriod(
                                        (short) 1,
                                        LocalDate.of(
                                                2026,
                                                9,
                                                1)),
                                null,
                                TODAY,
                                1,
                                DurationUnit.MONTH))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Renewal start date must be provided "
                                + "for an expired membership.");
    }

    @Test
    void rejectsExpiredMembershipStartDateInPast() {
        assertThatThrownBy(
                () ->
                        MembershipRenewalPolicy.evaluate(
                                MEMBERSHIP_ID,
                                MembershipStatus.EXPIRED,
                                currentPeriod(
                                        (short) 1,
                                        LocalDate.of(
                                                2026,
                                                9,
                                                1)),
                                TODAY.minusDays(1),
                                TODAY,
                                1,
                                DurationUnit.MONTH))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Renewal start date must not be before "
                                + "the current operational date.");
    }

    @Test
    void rejectsPeriodNumberOverflow() {
        assertThatThrownBy(
                () ->
                        MembershipRenewalPolicy.evaluate(
                                MEMBERSHIP_ID,
                                MembershipStatus.ACTIVE,
                                currentPeriod(
                                        Short.MAX_VALUE,
                                        LocalDate.of(
                                                2026,
                                                10,
                                                15)),
                                null,
                                TODAY,
                                1,
                                DurationUnit.MONTH))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership period number has reached "
                                + "its supported limit.");
    }

    @Test
    void rejectsMissingMembershipIdentifier() {
        assertThatThrownBy(
                () ->
                        MembershipRenewalPolicy.evaluate(
                                null,
                                MembershipStatus.ACTIVE,
                                currentPeriod(
                                        (short) 1,
                                        LocalDate.of(
                                                2026,
                                                10,
                                                15)),
                                null,
                                TODAY,
                                1,
                                DurationUnit.MONTH))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership identifier must be provided.");
    }

    @Test
    void rejectsMissingMembershipStatus() {
        assertThatThrownBy(
                () ->
                        MembershipRenewalPolicy.evaluate(
                                MEMBERSHIP_ID,
                                null,
                                currentPeriod(
                                        (short) 1,
                                        LocalDate.of(
                                                2026,
                                                10,
                                                15)),
                                null,
                                TODAY,
                                1,
                                DurationUnit.MONTH))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership status must be provided.");
    }

    @Test
    void rejectsMissingCurrentPeriod() {
        assertThatThrownBy(
                () ->
                        MembershipRenewalPolicy.evaluate(
                                MEMBERSHIP_ID,
                                MembershipStatus.ACTIVE,
                                null,
                                null,
                                TODAY,
                                1,
                                DurationUnit.MONTH))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Current membership period must be provided.");
    }

    @Test
    void rejectsMissingOperationalDate() {
        assertThatThrownBy(
                () ->
                        MembershipRenewalPolicy.evaluate(
                                MEMBERSHIP_ID,
                                MembershipStatus.ACTIVE,
                                currentPeriod(
                                        (short) 1,
                                        LocalDate.of(
                                                2026,
                                                10,
                                                15)),
                                null,
                                null,
                                1,
                                DurationUnit.MONTH))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Current operational date must be provided.");
    }

    @Test
    void rejectsNonPositiveCurrentPeriodNumber() {
        assertThatThrownBy(
                () ->
                        MembershipRenewalPolicy.evaluate(
                                MEMBERSHIP_ID,
                                MembershipStatus.ACTIVE,
                                currentPeriod(
                                        (short) 0,
                                        LocalDate.of(
                                                2026,
                                                10,
                                                15)),
                                null,
                                TODAY,
                                1,
                                DurationUnit.MONTH))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Current membership period number "
                                + "must be positive.");
    }

    private static MembershipPeriodDetails currentPeriod(
            short periodNumber,
            LocalDate effectiveEndsOn) {

        MembershipPricingSnapshot pricing =
                MembershipPricingSnapshot.withoutPromotion(
                        PLAN_ID,
                        "PLAN-000001",
                        "Monthly Access",
                        1,
                        DurationUnit.MONTH,
                        new BigDecimal("25.00"),
                        "USD");

        return new MembershipPeriodDetails(
                PERIOD_ID,
                periodNumber,
                periodNumber == 1
                        ? MembershipPeriodSource.INITIAL
                        : MembershipPeriodSource.RENEWAL,
                pricing,
                effectiveEndsOn.minusMonths(1),
                effectiveEndsOn,
                effectiveEndsOn,
                NOW,
                0);
    }
}
