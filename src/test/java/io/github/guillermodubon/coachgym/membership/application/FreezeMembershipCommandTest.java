package io.github.guillermodubon.coachgym.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.membership.domain.MembershipValidationException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FreezeMembershipCommandTest {

    private static final LocalDate STARTS_ON =
            LocalDate.of(2026, 9, 1);

    private static final LocalDate PLANNED_ENDS_ON =
            LocalDate.of(2026, 9, 15);

    @Test
    void shouldCreateAValidFreezeCommand() {
        FreezeMembershipCommand command =
                new FreezeMembershipCommand(
                        STARTS_ON,
                        PLANNED_ENDS_ON,
                        "  Medical leave  ",
                        2L);

        assertThat(command.startsOn())
                .isEqualTo(STARTS_ON);

        assertThat(command.plannedEndsOn())
                .isEqualTo(PLANNED_ENDS_ON);

        assertThat(command.reason())
                .isEqualTo("Medical leave");

        assertThat(command.version())
                .isEqualTo(2L);
    }

    @Test
    void shouldRejectMissingStartDate() {
        assertThatThrownBy(
                () -> new FreezeMembershipCommand(
                        null,
                        PLANNED_ENDS_ON,
                        "Medical leave",
                        0L))
                .isInstanceOf(MembershipValidationException.class)
                .hasMessage(
                        "Membership freeze start date "
                                + "must be provided.");
    }

    @Test
    void shouldRejectMissingPlannedEndDate() {
        assertThatThrownBy(
                () -> new FreezeMembershipCommand(
                        STARTS_ON,
                        null,
                        "Medical leave",
                        0L))
                .isInstanceOf(MembershipValidationException.class)
                .hasMessage(
                        "Membership freeze planned end date "
                                + "must be provided.");
    }

    @Test
    void shouldRejectEqualStartAndEndDates() {
        assertThatThrownBy(
                () -> new FreezeMembershipCommand(
                        STARTS_ON,
                        STARTS_ON,
                        "Medical leave",
                        0L))
                .isInstanceOf(MembershipValidationException.class)
                .hasMessage(
                        "Membership freeze planned end date "
                                + "must be after its start date.");
    }

    @Test
    void shouldRejectEndDateBeforeStartDate() {
        assertThatThrownBy(
                () -> new FreezeMembershipCommand(
                        STARTS_ON,
                        STARTS_ON.minusDays(1),
                        "Medical leave",
                        0L))
                .isInstanceOf(MembershipValidationException.class)
                .hasMessage(
                        "Membership freeze planned end date "
                                + "must be after its start date.");
    }

    @Test
    void shouldRejectBlankReason() {
        assertThatThrownBy(
                () -> new FreezeMembershipCommand(
                        STARTS_ON,
                        PLANNED_ENDS_ON,
                        "   ",
                        0L))
                .isInstanceOf(MembershipValidationException.class)
                .hasMessage(
                        "Membership freeze reason "
                                + "must not be blank.");
    }

    @Test
    void shouldRejectAnExcessivelyLongReason() {
        String reason = "a".repeat(2_001);

        assertThatThrownBy(
                () -> new FreezeMembershipCommand(
                        STARTS_ON,
                        PLANNED_ENDS_ON,
                        reason,
                        0L))
                .isInstanceOf(MembershipValidationException.class)
                .hasMessage(
                        "Membership freeze reason must not exceed "
                                + "2000 characters.");
    }

    @Test
    void shouldRejectNegativeVersion() {
        assertThatThrownBy(
                () -> new FreezeMembershipCommand(
                        STARTS_ON,
                        PLANNED_ENDS_ON,
                        "Medical leave",
                        -1L))
                .isInstanceOf(MembershipValidationException.class)
                .hasMessage(
                        "Membership version must not be negative.");
    }
}