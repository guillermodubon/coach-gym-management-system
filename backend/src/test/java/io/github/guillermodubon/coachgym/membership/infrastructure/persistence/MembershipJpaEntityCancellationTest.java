package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MembershipJpaEntityCancellationTest {

    private static final UUID CLIENT_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "20000000-0000-0000-0000-000000000001");

    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(
                    ACTOR_ID,
                    "coach-admin");

    private static final Instant CREATED_AT =
            Instant.parse(
                    "2026-09-01T14:00:00Z");

    private static final Instant CANCELLED_AT =
            Instant.parse(
                    "2026-09-15T14:00:00Z");

    private static final LocalDate CANCELLED_ON =
            LocalDate.of(
                    2026,
                    9,
                    15);

    @Test
    void shouldCancelActiveMembership() {
        MembershipJpaEntity membership =
                membership(
                        MembershipStatus.ACTIVE);

        membership.cancel(
                MembershipStatus.ACTIVE,
                CANCELLED_ON,
                "  Client requested cancellation  ",
                ACTOR,
                CANCELLED_AT);

        assertThat(membership.status())
                .isEqualTo(
                        MembershipStatus.CANCELLED);

        assertThat(membership.cancelledAt())
                .isEqualTo(CANCELLED_AT);

        assertThat(membership.cancelledByUserId())
                .isEqualTo(ACTOR_ID);

        assertThat(membership.cancellationReason())
                .isEqualTo(
                        "Client requested cancellation");

        assertThat(membership.updatedAt())
                .isEqualTo(CANCELLED_AT);
    }

    @Test
    void shouldCancelFrozenMembership() {
        MembershipJpaEntity membership =
                membership(
                        MembershipStatus.FROZEN);

        membership.cancel(
                MembershipStatus.FROZEN,
                CANCELLED_ON,
                "Client requested cancellation",
                ACTOR,
                CANCELLED_AT);

        assertThat(membership.status())
                .isEqualTo(
                        MembershipStatus.CANCELLED);

        assertThat(membership.cancelledAt())
                .isEqualTo(CANCELLED_AT);

        assertThat(membership.cancelledByUserId())
                .isEqualTo(ACTOR_ID);
    }

    @Test
    void shouldRejectUnexpectedPreviousStatus() {
        MembershipJpaEntity membership =
                membership(
                        MembershipStatus.ACTIVE);

        assertThatThrownBy(
                () ->
                        membership.cancel(
                                MembershipStatus.FROZEN,
                                CANCELLED_ON,
                                "Client requested cancellation",
                                ACTOR,
                                CANCELLED_AT))
                .isInstanceOf(
                        IllegalStateException.class);
    }

    @Test
    void shouldRejectBlankCancellationReason() {
        MembershipJpaEntity membership =
                membership(
                        MembershipStatus.ACTIVE);

        assertThatThrownBy(
                () ->
                        membership.cancel(
                                MembershipStatus.ACTIVE,
                                CANCELLED_ON,
                                " ",
                                ACTOR,
                                CANCELLED_AT))
                .isInstanceOf(
                        IllegalArgumentException.class)
                .hasMessage(
                        "Membership cancellation reason "
                                + "must not be blank.");
    }

    @Test
    void shouldRejectMissingActor() {
        MembershipJpaEntity membership =
                membership(
                        MembershipStatus.ACTIVE);

        assertThatThrownBy(
                () ->
                        membership.cancel(
                                MembershipStatus.ACTIVE,
                                CANCELLED_ON,
                                "Client requested cancellation",
                                null,
                                CANCELLED_AT))
                .isInstanceOf(
                        IllegalArgumentException.class)
                .hasMessage(
                        "Authenticated actor must be provided.");
    }

    private static MembershipJpaEntity membership(
            MembershipStatus status) {

        MembershipJpaEntity membership =
                MembershipJpaEntity.create(
                        CLIENT_ID,
                        ACTOR,
                        CREATED_AT);

        ReflectionTestUtils.setField(
                membership,
                "status",
                status);

        return membership;
    }
}