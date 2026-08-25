package io.github.guillermodubon.coachgym.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.membership.MembershipPeriodDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodSource;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.application.MembershipAlreadyCancelledException;
import io.github.guillermodubon.coachgym.membership.application.MembershipCancellationStateConflictException;
import io.github.guillermodubon.coachgym.plan.DurationUnit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipCancellationPolicyTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString(
                    "20000000-0000-0000-0000-000000000001");

    private static final UUID PLAN_ID =
            UUID.fromString(
                    "30000000-0000-0000-0000-000000000001");

    private static final LocalDate PERIOD_STARTS_ON =
            LocalDate.of(
                    2026,
                    9,
                    1);

    private static final LocalDate PERIOD_ENDS_ON =
            LocalDate.of(
                    2026,
                    10,
                    1);

    private static final LocalDate TODAY =
            LocalDate.of(
                    2026,
                    9,
                    15);

    private static final String CANCELLATION_REASON =
            "Client requested cancellation";

    @Test
    void shouldAllowActiveMembershipCancellation() {
        MembershipCancellation cancellation =
                MembershipCancellationPolicy
                        .createCancellation(
                                MEMBERSHIP_ID,
                                MembershipStatus.ACTIVE,
                                currentPeriod(),
                                TODAY,
                                TODAY,
                                CANCELLATION_REASON);

        assertThat(cancellation.membershipId())
                .isEqualTo(MEMBERSHIP_ID);

        assertThat(cancellation.cancelledOn())
                .isEqualTo(TODAY);

        assertThat(cancellation.reason())
                .isEqualTo(CANCELLATION_REASON);

        assertThat(cancellation.previousStatus())
                .isEqualTo(
                        MembershipStatus.ACTIVE);

        assertThat(cancellation.resultingStatus())
                .isEqualTo(
                        MembershipStatus.CANCELLED);

        assertThat(cancellation.closesOpenFreeze())
                .isFalse();
    }

    @Test
    void shouldAllowFrozenMembershipCancellation() {
        MembershipCancellation cancellation =
                MembershipCancellationPolicy
                        .createCancellation(
                                MEMBERSHIP_ID,
                                MembershipStatus.FROZEN,
                                currentPeriod(),
                                TODAY,
                                TODAY,
                                CANCELLATION_REASON);

        assertThat(cancellation.membershipId())
                .isEqualTo(MEMBERSHIP_ID);

        assertThat(cancellation.cancelledOn())
                .isEqualTo(TODAY);

        assertThat(cancellation.previousStatus())
                .isEqualTo(
                        MembershipStatus.FROZEN);

        assertThat(cancellation.resultingStatus())
                .isEqualTo(
                        MembershipStatus.CANCELLED);

        assertThat(cancellation.closesOpenFreeze())
                .isTrue();
    }

    @Test
    void shouldRejectAlreadyCancelledMembership() {
        assertThatThrownBy(
                () ->
                        MembershipCancellationPolicy
                                .createCancellation(
                                        MEMBERSHIP_ID,
                                        MembershipStatus.CANCELLED,
                                        currentPeriod(),
                                        TODAY,
                                        TODAY,
                                        "Duplicate cancellation"))
                .isInstanceOf(
                        MembershipAlreadyCancelledException.class)
                .hasMessageContaining(
                        "is already cancelled");
    }

    @Test
    void shouldRejectExpiredMembership() {
        assertThatThrownBy(
                () ->
                        MembershipCancellationPolicy
                                .createCancellation(
                                        MEMBERSHIP_ID,
                                        MembershipStatus.EXPIRED,
                                        currentPeriod(),
                                        TODAY,
                                        TODAY,
                                        CANCELLATION_REASON))
                .isInstanceOf(
                        MembershipCancellationStateConflictException.class)
                .hasMessageContaining(
                        MEMBERSHIP_ID.toString())
                .hasMessageContaining(
                        MembershipStatus.EXPIRED.name());
    }

    @Test
    void shouldRejectCancellationBeforePeriodStart() {
        LocalDate cancelledOn =
                PERIOD_STARTS_ON.minusDays(1);

        assertThatThrownBy(
                () ->
                        MembershipCancellationPolicy
                                .createCancellation(
                                        MEMBERSHIP_ID,
                                        MembershipStatus.ACTIVE,
                                        currentPeriod(),
                                        cancelledOn,
                                        TODAY,
                                        CANCELLATION_REASON))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership cancellation date must not be "
                                + "before the current period start date.");
    }

    @Test
    void shouldRejectCancellationAfterPeriodEnd() {
        LocalDate cancelledOn =
                PERIOD_ENDS_ON.plusDays(1);

        LocalDate operationalDate =
                cancelledOn;

        assertThatThrownBy(
                () ->
                        MembershipCancellationPolicy
                                .createCancellation(
                                        MEMBERSHIP_ID,
                                        MembershipStatus.ACTIVE,
                                        currentPeriod(),
                                        cancelledOn,
                                        operationalDate,
                                        CANCELLATION_REASON))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership cancellation date must not be "
                                + "after the current period end date.");
    }

    @Test
    void shouldRejectFutureCancellationDate() {
        LocalDate cancelledOn =
                TODAY.plusDays(1);

        assertThatThrownBy(
                () ->
                        MembershipCancellationPolicy
                                .createCancellation(
                                        MEMBERSHIP_ID,
                                        MembershipStatus.ACTIVE,
                                        currentPeriod(),
                                        cancelledOn,
                                        TODAY,
                                        CANCELLATION_REASON))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership cancellation date must not be "
                                + "after the current operational date.");
    }

    @Test
    void shouldAllowCancellationOnPeriodStart() {
        MembershipCancellation cancellation =
                MembershipCancellationPolicy
                        .createCancellation(
                                MEMBERSHIP_ID,
                                MembershipStatus.ACTIVE,
                                currentPeriod(),
                                PERIOD_STARTS_ON,
                                TODAY,
                                CANCELLATION_REASON);

        assertThat(cancellation.cancelledOn())
                .isEqualTo(
                        PERIOD_STARTS_ON);
    }

    @Test
    void shouldAllowCancellationOnPeriodEnd() {
        MembershipCancellation cancellation =
                MembershipCancellationPolicy
                        .createCancellation(
                                MEMBERSHIP_ID,
                                MembershipStatus.ACTIVE,
                                currentPeriod(),
                                PERIOD_ENDS_ON,
                                PERIOD_ENDS_ON,
                                CANCELLATION_REASON);

        assertThat(cancellation.cancelledOn())
                .isEqualTo(
                        PERIOD_ENDS_ON);
    }

    @Test
    void shouldRejectMissingMembershipId() {
        assertThatThrownBy(
                () ->
                        MembershipCancellationPolicy
                                .createCancellation(
                                        null,
                                        MembershipStatus.ACTIVE,
                                        currentPeriod(),
                                        TODAY,
                                        TODAY,
                                        CANCELLATION_REASON))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership identifier must be provided.");
    }

    @Test
    void shouldRejectMissingMembershipStatus() {
        assertThatThrownBy(
                () ->
                        MembershipCancellationPolicy
                                .createCancellation(
                                        MEMBERSHIP_ID,
                                        null,
                                        currentPeriod(),
                                        TODAY,
                                        TODAY,
                                        CANCELLATION_REASON))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership status must be provided.");
    }

    @Test
    void shouldRejectMissingCurrentPeriod() {
        assertThatThrownBy(
                () ->
                        MembershipCancellationPolicy
                                .createCancellation(
                                        MEMBERSHIP_ID,
                                        MembershipStatus.ACTIVE,
                                        null,
                                        TODAY,
                                        TODAY,
                                        CANCELLATION_REASON))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Current membership period must be provided.");
    }

    @Test
    void shouldRejectMissingCancellationDate() {
        assertThatThrownBy(
                () ->
                        MembershipCancellationPolicy
                                .createCancellation(
                                        MEMBERSHIP_ID,
                                        MembershipStatus.ACTIVE,
                                        currentPeriod(),
                                        null,
                                        TODAY,
                                        CANCELLATION_REASON))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership cancellation date "
                                + "must be provided.");
    }

    @Test
    void shouldRejectMissingOperationalDate() {
        assertThatThrownBy(
                () ->
                        MembershipCancellationPolicy
                                .createCancellation(
                                        MEMBERSHIP_ID,
                                        MembershipStatus.ACTIVE,
                                        currentPeriod(),
                                        TODAY,
                                        null,
                                        CANCELLATION_REASON))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Current operational date must be provided.");
    }

    @Test
    void shouldRejectMissingReason() {
        assertThatThrownBy(
                () ->
                        MembershipCancellationPolicy
                                .createCancellation(
                                        MEMBERSHIP_ID,
                                        MembershipStatus.ACTIVE,
                                        currentPeriod(),
                                        TODAY,
                                        TODAY,
                                        null))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership cancellation reason "
                                + "must not be blank.");
    }

    @Test
    void shouldRejectBlankReason() {
        assertThatThrownBy(
                () ->
                        MembershipCancellationPolicy
                                .createCancellation(
                                        MEMBERSHIP_ID,
                                        MembershipStatus.ACTIVE,
                                        currentPeriod(),
                                        TODAY,
                                        TODAY,
                                        "   "))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership cancellation reason "
                                + "must not be blank.");
    }

    private static MembershipPeriodDetails currentPeriod() {
        MembershipPricingSnapshot pricing =
                MembershipPricingSnapshot
                        .withoutPromotion(
                                PLAN_ID,
                                "PLAN-000001",
                                "Monthly Access",
                                1,
                                DurationUnit.MONTH,
                                new BigDecimal(
                                        "25.00"),
                                "USD");

        return new MembershipPeriodDetails(
                PERIOD_ID,
                (short) 1,
                MembershipPeriodSource.INITIAL,
                pricing,
                PERIOD_STARTS_ON,
                PERIOD_ENDS_ON,
                PERIOD_ENDS_ON,
                Instant.parse(
                        "2026-09-01T14:00:00Z"),
                0L);
    }
}