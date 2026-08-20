package io.github.guillermodubon.coachgym.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.membership.domain.MembershipValidationException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReactivateMembershipCommandTest {

    @Test
    void shouldCreateAValidReactivationCommand() {
        LocalDate reactivatedOn =
                LocalDate.of(2026, 9, 10);

        ReactivateMembershipCommand command =
                new ReactivateMembershipCommand(
                        reactivatedOn,
                        3L);

        assertThat(command.reactivatedOn())
                .isEqualTo(reactivatedOn);

        assertThat(command.version())
                .isEqualTo(3L);
    }

    @Test
    void shouldRejectMissingReactivationDate() {
        assertThatThrownBy(
                () -> new ReactivateMembershipCommand(
                        null,
                        0L))
                .isInstanceOf(MembershipValidationException.class)
                .hasMessage(
                        "Membership reactivation date "
                                + "must be provided.");
    }

    @Test
    void shouldRejectNegativeVersion() {
        assertThatThrownBy(
                () -> new ReactivateMembershipCommand(
                        LocalDate.of(2026, 9, 10),
                        -1L))
                .isInstanceOf(MembershipValidationException.class)
                .hasMessage(
                        "Membership version must not be negative.");
    }
}