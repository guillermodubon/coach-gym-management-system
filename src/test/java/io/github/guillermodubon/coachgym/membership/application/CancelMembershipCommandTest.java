package io.github.guillermodubon.coachgym.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.membership.domain.MembershipValidationException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CancelMembershipCommandTest {

    private static final LocalDate CANCELLED_ON =
            LocalDate.of(
                    2026,
                    9,
                    15);

    @Test
    void shouldCreateCancellationCommand() {
        CancelMembershipCommand command =
                new CancelMembershipCommand(
                        CANCELLED_ON,
                        "  Client requested cancellation  ",
                        2L);

        assertThat(command.cancelledOn())
                .isEqualTo(CANCELLED_ON);

        assertThat(command.reason())
                .isEqualTo(
                        "Client requested cancellation");

        assertThat(command.version())
                .isEqualTo(2L);
    }

    @Test
    void shouldRejectMissingCancellationDate() {
        assertThatThrownBy(
                () ->
                        new CancelMembershipCommand(
                                null,
                                "Client requested cancellation",
                                0L))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership cancellation date "
                                + "must be provided.");
    }

    @Test
    void shouldRejectNullReason() {
        assertThatThrownBy(
                () ->
                        new CancelMembershipCommand(
                                CANCELLED_ON,
                                null,
                                0L))
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
                        new CancelMembershipCommand(
                                CANCELLED_ON,
                                "   ",
                                0L))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership cancellation reason "
                                + "must not be blank.");
    }

    @Test
    void shouldRejectReasonLongerThanMaximum() {
        String reason =
                "a".repeat(2_001);

        assertThatThrownBy(
                () ->
                        new CancelMembershipCommand(
                                CANCELLED_ON,
                                reason,
                                0L))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership cancellation reason must "
                                + "not exceed 2000 characters.");
    }

    @Test
    void shouldAcceptReasonAtMaximumLength() {
        String reason =
                "a".repeat(2_000);

        CancelMembershipCommand command =
                new CancelMembershipCommand(
                        CANCELLED_ON,
                        reason,
                        0L);

        assertThat(command.reason())
                .hasSize(2_000);
    }

    @Test
    void shouldRejectNegativeVersion() {
        assertThatThrownBy(
                () ->
                        new CancelMembershipCommand(
                                CANCELLED_ON,
                                "Client requested cancellation",
                                -1L))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership version must not be negative.");
    }
}
