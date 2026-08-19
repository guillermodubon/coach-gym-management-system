package io.github.guillermodubon.coachgym.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.membership.domain.MembershipValidationException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RenewMembershipCommandTest {

    private static final UUID PLAN_ID =
            UUID.fromString(
                    "a7f2881a-c1e9-46cd-b10f-abe22088fc09");

    @Test
    void acceptsValidRenewalCommand() {
        RenewMembershipCommand command =
                new RenewMembershipCommand(
                        PLAN_ID,
                        null,
                        LocalDate.of(2026, 10, 1),
                        2);

        assertThat(command.membershipPlanId())
                .isEqualTo(PLAN_ID);

        assertThat(command.promotionId())
                .isNull();

        assertThat(command.version())
                .isEqualTo(2);
    }

    @Test
    void acceptsMissingStartDateForActiveMembership() {
        RenewMembershipCommand command =
                new RenewMembershipCommand(
                        PLAN_ID,
                        null,
                        null,
                        0);

        assertThat(command.startsOn())
                .isNull();
    }

    @Test
    void rejectsMissingPlanIdentifier() {
        assertThatThrownBy(
                () ->
                        new RenewMembershipCommand(
                                null,
                                null,
                                LocalDate.of(
                                        2026,
                                        10,
                                        1),
                                0))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Renewal membership plan identifier "
                                + "must be provided.");
    }

    @Test
    void rejectsNegativeVersion() {
        assertThatThrownBy(
                () ->
                        new RenewMembershipCommand(
                                PLAN_ID,
                                null,
                                LocalDate.of(
                                        2026,
                                        10,
                                        1),
                                -1))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership version must not be negative.");
    }
}
