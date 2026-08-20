package io.github.guillermodubon.coachgym.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.membership.MembershipFreezeDetails;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.application.MembershipAlreadyFrozenException;
import io.github.guillermodubon.coachgym.membership.application.MembershipFreezeStateConflictException;
import io.github.guillermodubon.coachgym.membership.application.MembershipNotFrozenException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipFreezePolicyTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString(
                    "20000000-0000-0000-0000-000000000001");

    private static final UUID FREEZE_ID =
            UUID.fromString(
                    "30000000-0000-0000-0000-000000000001");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "40000000-0000-0000-0000-000000000001");

    private static final LocalDate STARTS_ON =
            LocalDate.of(2026, 9, 1);

    private static final LocalDate PLANNED_ENDS_ON =
            LocalDate.of(2026, 9, 15);

    private static final Instant OCCURRED_AT =
            Instant.parse("2026-09-01T14:00:00Z");

    @Test
    void shouldAllowFreezingAnActiveMembership() {
        MembershipFreeze freeze =
                MembershipFreezePolicy.createFreeze(
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        MembershipStatus.ACTIVE,
                        false,
                        STARTS_ON,
                        PLANNED_ENDS_ON,
                        "Medical leave");

        assertThat(freeze.membershipId())
                .isEqualTo(MEMBERSHIP_ID);

        assertThat(freeze.membershipPeriodId())
                .isEqualTo(PERIOD_ID);

        assertThat(freeze.startsOn())
                .isEqualTo(STARTS_ON);

        assertThat(freeze.plannedEndsOn())
                .isEqualTo(PLANNED_ENDS_ON);

        assertThat(freeze.reason())
                .isEqualTo("Medical leave");
    }

    @Test
    void shouldRejectAFrozenMembership() {
        assertThatThrownBy(
                () -> MembershipFreezePolicy.createFreeze(
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        MembershipStatus.FROZEN,
                        true,
                        STARTS_ON,
                        PLANNED_ENDS_ON,
                        "Medical leave"))
                .isInstanceOf(
                        MembershipAlreadyFrozenException.class)
                .hasMessage(
                        "Membership "
                                + MEMBERSHIP_ID
                                + " is already frozen.");
    }

    @Test
    void shouldRejectAnOpenFreezeEvenIfStatusIsActive() {
        assertThatThrownBy(
                () -> MembershipFreezePolicy.createFreeze(
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        MembershipStatus.ACTIVE,
                        true,
                        STARTS_ON,
                        PLANNED_ENDS_ON,
                        "Medical leave"))
                .isInstanceOf(
                        MembershipAlreadyFrozenException.class);
    }

    @Test
    void shouldRejectFreezingAnExpiredMembership() {
        assertThatThrownBy(
                () -> MembershipFreezePolicy.createFreeze(
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        MembershipStatus.EXPIRED,
                        false,
                        STARTS_ON,
                        PLANNED_ENDS_ON,
                        "Medical leave"))
                .isInstanceOf(
                        MembershipFreezeStateConflictException.class)
                .hasMessage(
                        "Membership "
                                + MEMBERSHIP_ID
                                + " cannot be frozen while its status is "
                                + MembershipStatus.EXPIRED
                                + ".");
    }

    @Test
    void shouldRejectFreezingACancelledMembership() {
        assertThatThrownBy(
                () -> MembershipFreezePolicy.createFreeze(
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        MembershipStatus.CANCELLED,
                        false,
                        STARTS_ON,
                        PLANNED_ENDS_ON,
                        "Medical leave"))
                .isInstanceOf(
                        MembershipFreezeStateConflictException.class);
    }

    @Test
    void shouldAllowReactivatingAFrozenMembership() {
        MembershipFreezeDetails openFreeze =
                openFreeze(MEMBERSHIP_ID);

        assertThatCode(
                () -> MembershipFreezePolicy.validateReactivation(
                        MEMBERSHIP_ID,
                        MembershipStatus.FROZEN,
                        openFreeze,
                        LocalDate.of(2026, 9, 10)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowReactivationOnFreezeStartDate() {
        MembershipFreezeDetails openFreeze =
                openFreeze(MEMBERSHIP_ID);

        assertThatCode(
                () -> MembershipFreezePolicy.validateReactivation(
                        MEMBERSHIP_ID,
                        MembershipStatus.FROZEN,
                        openFreeze,
                        STARTS_ON))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectReactivatingAnActiveMembership() {
        assertThatThrownBy(
                () -> MembershipFreezePolicy.validateReactivation(
                        MEMBERSHIP_ID,
                        MembershipStatus.ACTIVE,
                        openFreeze(MEMBERSHIP_ID),
                        LocalDate.of(2026, 9, 10)))
                .isInstanceOf(
                        MembershipNotFrozenException.class)
                .hasMessage(
                        "Membership "
                                + MEMBERSHIP_ID
                                + " cannot be reactivated while its status is "
                                + MembershipStatus.ACTIVE
                                + ".");
    }

    @Test
    void shouldRejectMissingOpenFreeze() {
        assertThatThrownBy(
                () -> MembershipFreezePolicy.validateReactivation(
                        MEMBERSHIP_ID,
                        MembershipStatus.FROZEN,
                        null,
                        LocalDate.of(2026, 9, 10)))
                .isInstanceOf(
                        MembershipNotFrozenException.class);
    }

    @Test
    void shouldRejectAClosedFreeze() {
        MembershipFreezeDetails closedFreeze =
                closedFreeze(MEMBERSHIP_ID);

        assertThatThrownBy(
                () -> MembershipFreezePolicy.validateReactivation(
                        MEMBERSHIP_ID,
                        MembershipStatus.FROZEN,
                        closedFreeze,
                        LocalDate.of(2026, 9, 12)))
                .isInstanceOf(
                        MembershipNotFrozenException.class);
    }

    @Test
    void shouldRejectAFreezeFromAnotherMembership() {
        UUID anotherMembershipId =
                UUID.fromString(
                        "10000000-0000-0000-0000-000000000099");

        assertThatThrownBy(
                () -> MembershipFreezePolicy.validateReactivation(
                        MEMBERSHIP_ID,
                        MembershipStatus.FROZEN,
                        openFreeze(anotherMembershipId),
                        LocalDate.of(2026, 9, 10)))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Open membership freeze does not belong "
                                + "to the requested membership.");
    }

    @Test
    void shouldRejectReactivationBeforeFreezeStart() {
        assertThatThrownBy(
                () -> MembershipFreezePolicy.validateReactivation(
                        MEMBERSHIP_ID,
                        MembershipStatus.FROZEN,
                        openFreeze(MEMBERSHIP_ID),
                        STARTS_ON.minusDays(1)))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership reactivation date must not be "
                                + "before the freeze start date.");
    }

    private static MembershipFreezeDetails openFreeze(
            UUID membershipId) {

        return new MembershipFreezeDetails(
                FREEZE_ID,
                membershipId,
                PERIOD_ID,
                STARTS_ON,
                PLANNED_ENDS_ON,
                "Medical leave",
                null,
                ACTOR_ID,
                null,
                OCCURRED_AT,
                OCCURRED_AT,
                0L);
    }

    private static MembershipFreezeDetails closedFreeze(
            UUID membershipId) {

        return new MembershipFreezeDetails(
                FREEZE_ID,
                membershipId,
                PERIOD_ID,
                STARTS_ON,
                PLANNED_ENDS_ON,
                "Medical leave",
                LocalDate.of(2026, 9, 10),
                ACTOR_ID,
                ACTOR_ID,
                OCCURRED_AT,
                OCCURRED_AT.plusSeconds(3_600),
                1L);
    }
}