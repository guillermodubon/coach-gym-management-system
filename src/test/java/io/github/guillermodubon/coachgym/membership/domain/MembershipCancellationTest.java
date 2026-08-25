package io.github.guillermodubon.coachgym.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipCancellationTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString(
                    "20000000-0000-0000-0000-000000000001");

    private static final LocalDate CANCELLED_ON =
            LocalDate.of(
                    2026,
                    9,
                    15);

    @Test
    void shouldCreateActiveMembershipCancellation() {
        MembershipCancellation cancellation =
                new MembershipCancellation(
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        CANCELLED_ON,
                        "  Client relocation  ",
                        MembershipStatus.ACTIVE);

        assertThat(cancellation.membershipId())
                .isEqualTo(MEMBERSHIP_ID);

        assertThat(cancellation.membershipPeriodId())
                .isEqualTo(PERIOD_ID);

        assertThat(cancellation.cancelledOn())
                .isEqualTo(CANCELLED_ON);

        assertThat(cancellation.reason())
                .isEqualTo("Client relocation");

        assertThat(cancellation.previousStatus())
                .isEqualTo(MembershipStatus.ACTIVE);

        assertThat(cancellation.resultingStatus())
                .isEqualTo(MembershipStatus.CANCELLED);

        assertThat(cancellation.closesOpenFreeze())
                .isFalse();
    }

    @Test
    void shouldIdentifyFrozenCancellationAsClosingFreeze() {
        MembershipCancellation cancellation =
                new MembershipCancellation(
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        CANCELLED_ON,
                        "Client requested cancellation",
                        MembershipStatus.FROZEN);

        assertThat(cancellation.previousStatus())
                .isEqualTo(MembershipStatus.FROZEN);

        assertThat(cancellation.resultingStatus())
                .isEqualTo(MembershipStatus.CANCELLED);

        assertThat(cancellation.closesOpenFreeze())
                .isTrue();
    }

    @Test
    void shouldRejectMissingMembershipId() {
        assertThatThrownBy(
                () ->
                        new MembershipCancellation(
                                null,
                                PERIOD_ID,
                                CANCELLED_ON,
                                "Client requested cancellation",
                                MembershipStatus.ACTIVE))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership identifier must be provided.");
    }

    @Test
    void shouldRejectMissingPeriodId() {
        assertThatThrownBy(
                () ->
                        new MembershipCancellation(
                                MEMBERSHIP_ID,
                                null,
                                CANCELLED_ON,
                                "Client requested cancellation",
                                MembershipStatus.ACTIVE))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership period identifier "
                                + "must be provided.");
    }

    @Test
    void shouldRejectMissingCancellationDate() {
        assertThatThrownBy(
                () ->
                        new MembershipCancellation(
                                MEMBERSHIP_ID,
                                PERIOD_ID,
                                null,
                                "Client requested cancellation",
                                MembershipStatus.ACTIVE))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership cancellation date "
                                + "must be provided.");
    }

    @Test
    void shouldRejectBlankReason() {
        assertThatThrownBy(
                () ->
                        new MembershipCancellation(
                                MEMBERSHIP_ID,
                                PERIOD_ID,
                                CANCELLED_ON,
                                " ",
                                MembershipStatus.ACTIVE))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership cancellation reason "
                                + "must not be blank.");
    }

    @Test
    void shouldRejectUnsupportedPreviousStatus() {
        assertThatThrownBy(
                () ->
                        new MembershipCancellation(
                                MEMBERSHIP_ID,
                                PERIOD_ID,
                                CANCELLED_ON,
                                "Client requested cancellation",
                                MembershipStatus.EXPIRED))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership cancellation previous status "
                                + "must be ACTIVE or FROZEN.");
    }
}
