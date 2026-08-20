package io.github.guillermodubon.coachgym.membership.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.membership.MembershipFreezeDetails;
import io.github.guillermodubon.coachgym.membership.domain.MembershipFreeze;
import io.github.guillermodubon.coachgym.membership.domain.MembershipValidationException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipFreezeJpaEntityTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString(
                    "20000000-0000-0000-0000-000000000001");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "30000000-0000-0000-0000-000000000001");

    private static final UUID REACTIVATION_ACTOR_ID =
            UUID.fromString(
                    "30000000-0000-0000-0000-000000000002");

    private static final LocalDate STARTS_ON =
            LocalDate.of(2026, 9, 1);

    private static final LocalDate PLANNED_ENDS_ON =
            LocalDate.of(2026, 9, 15);

    private static final Instant CREATED_AT =
            Instant.parse("2026-09-01T14:00:00Z");

    private static final Instant REACTIVATED_AT =
            Instant.parse("2026-09-10T15:00:00Z");

    @Test
    void shouldCreateAnOpenMembershipFreeze() {
        MembershipFreeze freeze =
                new MembershipFreeze(
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        STARTS_ON,
                        PLANNED_ENDS_ON,
                        "Medical leave");

        MembershipFreezeJpaEntity entity =
                MembershipFreezeJpaEntity.create(
                        freeze,
                        actor(ACTOR_ID),
                        CREATED_AT);

        MembershipFreezeDetails details =
                entity.toDetails();

        assertThat(details.id())
                .isNotNull();

        assertThat(details.membershipId())
                .isEqualTo(MEMBERSHIP_ID);

        assertThat(details.membershipPeriodId())
                .isEqualTo(PERIOD_ID);

        assertThat(details.startsOn())
                .isEqualTo(STARTS_ON);

        assertThat(details.plannedEndsOn())
                .isEqualTo(PLANNED_ENDS_ON);

        assertThat(details.reason())
                .isEqualTo("Medical leave");

        assertThat(details.reactivatedOn())
                .isNull();

        assertThat(details.createdByUserId())
                .isEqualTo(ACTOR_ID);

        assertThat(details.reactivatedByUserId())
                .isNull();

        assertThat(details.createdAt())
                .isEqualTo(CREATED_AT);

        assertThat(details.updatedAt())
                .isEqualTo(CREATED_AT);

        assertThat(details.version())
                .isZero();

        assertThat(entity.open())
                .isTrue();
    }

    @Test
    void shouldReactivateAnOpenFreeze() {
        MembershipFreezeJpaEntity entity =
                openFreezeEntity();

        LocalDate reactivatedOn =
                LocalDate.of(2026, 9, 10);

        entity.reactivate(
                reactivatedOn,
                actor(REACTIVATION_ACTOR_ID),
                REACTIVATED_AT);

        MembershipFreezeDetails details =
                entity.toDetails();

        assertThat(details.reactivatedOn())
                .isEqualTo(reactivatedOn);

        assertThat(details.reactivatedByUserId())
                .isEqualTo(REACTIVATION_ACTOR_ID);

        assertThat(details.updatedAt())
                .isEqualTo(REACTIVATED_AT);

        assertThat(entity.open())
                .isFalse();
    }

    @Test
    void shouldAllowReactivationOnFreezeStartDate() {
        MembershipFreezeJpaEntity entity =
                openFreezeEntity();

        entity.reactivate(
                STARTS_ON,
                actor(REACTIVATION_ACTOR_ID),
                REACTIVATED_AT);

        assertThat(entity.toDetails().reactivatedOn())
                .isEqualTo(STARTS_ON);
    }

    @Test
    void shouldRejectReactivationBeforeFreezeStart() {
        MembershipFreezeJpaEntity entity =
                openFreezeEntity();

        assertThatThrownBy(
                () -> entity.reactivate(
                        STARTS_ON.minusDays(1),
                        actor(REACTIVATION_ACTOR_ID),
                        REACTIVATED_AT))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership reactivation date must not be "
                                + "before the freeze start date.");
    }

    @Test
    void shouldRejectASecondReactivation() {
        MembershipFreezeJpaEntity entity =
                openFreezeEntity();

        entity.reactivate(
                LocalDate.of(2026, 9, 10),
                actor(REACTIVATION_ACTOR_ID),
                REACTIVATED_AT);

        assertThatThrownBy(
                () -> entity.reactivate(
                        LocalDate.of(2026, 9, 11),
                        actor(REACTIVATION_ACTOR_ID),
                        REACTIVATED_AT.plusSeconds(3_600)))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership freeze is already closed.");
    }

    @Test
    void shouldRejectMissingActorWhenCreating() {
        MembershipFreeze freeze =
                new MembershipFreeze(
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        STARTS_ON,
                        PLANNED_ENDS_ON,
                        "Medical leave");

        assertThatThrownBy(
                () -> MembershipFreezeJpaEntity.create(
                        freeze,
                        null,
                        CREATED_AT))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Authenticated actor must be provided.");
    }

    @Test
    void shouldRejectMissingOccurrenceTime() {
        MembershipFreeze freeze =
                new MembershipFreeze(
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        STARTS_ON,
                        PLANNED_ENDS_ON,
                        "Medical leave");

        assertThatThrownBy(
                () -> MembershipFreezeJpaEntity.create(
                        freeze,
                        actor(ACTOR_ID),
                        null))
                .isInstanceOf(
                        MembershipValidationException.class)
                .hasMessage(
                        "Membership freeze occurrence time "
                                + "must be provided.");
    }

    private static MembershipFreezeJpaEntity
    openFreezeEntity() {

        MembershipFreeze freeze =
                new MembershipFreeze(
                        MEMBERSHIP_ID,
                        PERIOD_ID,
                        STARTS_ON,
                        PLANNED_ENDS_ON,
                        "Medical leave");

        return MembershipFreezeJpaEntity.create(
                freeze,
                actor(ACTOR_ID),
                CREATED_AT);
    }

    private static AuthenticatedActor actor(
            UUID actorId) {

        return new AuthenticatedActor(
                actorId,
                "staff-user");
    }
}