package io.github.guillermodubon.coachgym.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipFreezeTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString(
                    "20000000-0000-0000-0000-000000000001");

    @Test
    void shouldCreateAValidMembershipFreeze() {
        MembershipFreeze freeze =
                new MembershipFreeze(
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 15),
                        "  Medical leave  ");

        assertThat(freeze.membershipId())
                .isEqualTo(MEMBERSHIP_ID);

        assertThat(freeze.membershipPeriodId())
                .isEqualTo(PERIOD_ID);

        assertThat(freeze.reason())
                .isEqualTo("Medical leave");
    }

    @Test
    void shouldRejectMissingMembershipIdentifier() {
        assertThatThrownBy(
                () -> new MembershipFreeze(
                        null,
                        PERIOD_ID,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 15),
                        "Medical leave"))
                .isInstanceOf(MembershipValidationException.class)
                .hasMessage(
                        "Membership identifier must be provided.");
    }

    @Test
    void shouldRejectMissingPeriodIdentifier() {
        assertThatThrownBy(
                () -> new MembershipFreeze(
                        MEMBERSHIP_ID,
                        null,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 15),
                        "Medical leave"))
                .isInstanceOf(MembershipValidationException.class)
                .hasMessage(
                        "Membership period identifier "
                                + "must be provided.");
    }

    @Test
    void shouldRejectInvalidDateRange() {
        LocalDate startsOn =
                LocalDate.of(2026, 9, 15);

        assertThatThrownBy(
                () -> new MembershipFreeze(
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        startsOn,
                        startsOn,
                        "Medical leave"))
                .isInstanceOf(MembershipValidationException.class)
                .hasMessage(
                        "Membership freeze planned end date "
                                + "must be after its start date.");
    }
}
