package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.membership.MembershipFrozen;
import io.github.guillermodubon.coachgym.membership.MembershipReactivated;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MembershipFreezeAuditEntryJpaEntityTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001");

    private static final UUID CLIENT_ID =
            UUID.fromString(
                    "20000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString(
                    "30000000-0000-0000-0000-000000000001");

    private static final UUID FREEZE_ID =
            UUID.fromString(
                    "40000000-0000-0000-0000-000000000001");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "50000000-0000-0000-0000-000000000001");

    private static final String MEMBERSHIP_CODE =
            "MEM-000001";

    private static final String ACTOR_IDENTIFIER =
            "coach-admin";

    private static final String FREEZE_REASON =
            "Medical leave";

    private static final LocalDate FREEZE_STARTS_ON =
            LocalDate.of(
                    2026,
                    9,
                    10);

    private static final LocalDate PLANNED_ENDS_ON =
            LocalDate.of(
                    2026,
                    9,
                    20);

    private static final LocalDate REACTIVATED_ON =
            LocalDate.of(
                    2026,
                    9,
                    15);

    private static final Instant OCCURRED_AT =
            Instant.parse(
                    "2026-09-15T14:00:00Z");

    @Test
    void shouldMapMembershipFrozenEvent() {
        MembershipFrozen event =
                membershipFrozen();

        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(event);

        assertThat(field(entry, "id"))
                .isNotNull();

        assertThat(field(entry, "actorUserId"))
                .isEqualTo(ACTOR_ID);

        assertThat(field(
                entry,
                "actorIdentifierSnapshot"))
                .isEqualTo(ACTOR_IDENTIFIER);

        assertThat(field(entry, "actionCode"))
                .isEqualTo("MEMBERSHIP_FROZEN");

        assertThat(field(entry, "resourceType"))
                .isEqualTo("MEMBERSHIP");

        assertThat(field(entry, "resourceId"))
                .isEqualTo(MEMBERSHIP_ID);

        assertThat(field(
                entry,
                "resourceCodeSnapshot"))
                .isEqualTo(MEMBERSHIP_CODE);

        assertThat(field(entry, "summary"))
                .isEqualTo("Membership frozen.");

        assertThat(field(entry, "occurredAt"))
                .isEqualTo(OCCURRED_AT);

        Map<String, Object> metadata =
                metadata(entry);

        assertThat(metadata)
                .containsEntry(
                        "clientId",
                        CLIENT_ID)
                .containsEntry(
                        "membershipPeriodId",
                        PERIOD_ID)
                .containsEntry(
                        "startsOn",
                        FREEZE_STARTS_ON.toString())
                .containsEntry(
                        "plannedEndsOn",
                        PLANNED_ENDS_ON.toString())
                .containsEntry(
                        "previousStatus",
                        MembershipStatus.ACTIVE.name())
                .containsEntry(
                        "resultingStatus",
                        MembershipStatus.FROZEN.name())
                .containsEntry(
                        "statusChanged",
                        true);
    }

    @Test
    void shouldMapMembershipReactivatedEvent() {
        MembershipReactivated event =
                membershipReactivated();

        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(event);

        assertThat(field(entry, "id"))
                .isNotNull();

        assertThat(field(entry, "actorUserId"))
                .isEqualTo(ACTOR_ID);

        assertThat(field(
                entry,
                "actorIdentifierSnapshot"))
                .isEqualTo(ACTOR_IDENTIFIER);

        assertThat(field(entry, "actionCode"))
                .isEqualTo(
                        "MEMBERSHIP_REACTIVATED");

        assertThat(field(entry, "resourceType"))
                .isEqualTo("MEMBERSHIP");

        assertThat(field(entry, "resourceId"))
                .isEqualTo(MEMBERSHIP_ID);

        assertThat(field(
                entry,
                "resourceCodeSnapshot"))
                .isEqualTo(MEMBERSHIP_CODE);

        assertThat(field(entry, "summary"))
                .isEqualTo(
                        "Membership reactivated.");

        assertThat(field(entry, "occurredAt"))
                .isEqualTo(OCCURRED_AT);

        Map<String, Object> metadata =
                metadata(entry);

        assertThat(metadata)
                .containsEntry(
                        "clientId",
                        CLIENT_ID)
                .containsEntry(
                        "membershipPeriodId",
                        PERIOD_ID)
                .containsEntry(
                        "membershipFreezeId",
                        FREEZE_ID)
                .containsEntry(
                        "freezeStartsOn",
                        FREEZE_STARTS_ON.toString())
                .containsEntry(
                        "plannedEndsOn",
                        PLANNED_ENDS_ON.toString())
                .containsEntry(
                        "reactivatedOn",
                        REACTIVATED_ON.toString())
                .containsEntry(
                        "reason",
                        FREEZE_REASON)
                .containsEntry(
                        "previousStatus",
                        MembershipStatus.FROZEN.name())
                .containsEntry(
                        "resultingStatus",
                        MembershipStatus.ACTIVE.name())
                .containsEntry(
                        "statusChanged",
                        true);
    }

    private static MembershipFrozen membershipFrozen() {
        return new MembershipFrozen(
                MEMBERSHIP_ID,
                MEMBERSHIP_CODE,
                CLIENT_ID,
                PERIOD_ID,
                FREEZE_STARTS_ON,
                PLANNED_ENDS_ON,
                FREEZE_REASON,
                MembershipStatus.ACTIVE,
                MembershipStatus.FROZEN,
                ACTOR_ID,
                ACTOR_IDENTIFIER,
                OCCURRED_AT);
    }

    private static MembershipReactivated
    membershipReactivated() {

        return new MembershipReactivated(
                MEMBERSHIP_ID,
                MEMBERSHIP_CODE,
                CLIENT_ID,
                PERIOD_ID,
                FREEZE_ID,
                FREEZE_STARTS_ON,
                PLANNED_ENDS_ON,
                REACTIVATED_ON,
                FREEZE_REASON,
                MembershipStatus.FROZEN,
                MembershipStatus.ACTIVE,
                ACTOR_ID,
                ACTOR_IDENTIFIER,
                OCCURRED_AT);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(
            AuditEntryJpaEntity entry) {

        return (Map<String, Object>)
                ReflectionTestUtils.getField(
                        entry,
                        "metadata");
    }

    private static Object field(
            AuditEntryJpaEntity entry,
            String fieldName) {

        return ReflectionTestUtils.getField(
                entry,
                fieldName);
    }
}