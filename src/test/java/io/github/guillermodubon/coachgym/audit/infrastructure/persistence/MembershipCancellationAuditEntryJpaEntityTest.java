package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.membership.MembershipCancelled;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MembershipCancellationAuditEntryJpaEntityTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001");

    private static final UUID CLIENT_ID =
            UUID.fromString(
                    "20000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString(
                    "30000000-0000-0000-0000-000000000001");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "40000000-0000-0000-0000-000000000001");

    private static final LocalDate CANCELLED_ON =
            LocalDate.of(
                    2026,
                    9,
                    15);

    private static final Instant OCCURRED_AT =
            Instant.parse(
                    "2026-09-15T14:00:00Z");

    @Test
    void shouldMapActiveMembershipCancellation() {
        MembershipCancelled event =
                activeMembershipCancellation();

        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(
                        event);

        assertThat(
                actorUserId(entry))
                .isEqualTo(ACTOR_ID);

        assertThat(
                actorIdentifier(entry))
                .isEqualTo("coach-admin");

        assertThat(
                actionCode(entry))
                .isEqualTo(
                        "MEMBERSHIP_CANCELLED");

        assertThat(
                resourceType(entry))
                .isEqualTo(
                        "MEMBERSHIP");

        assertThat(
                resourceId(entry))
                .isEqualTo(MEMBERSHIP_ID);

        assertThat(
                resourceCode(entry))
                .isEqualTo(
                        "MEM-000001");

        assertThat(
                summary(entry))
                .isEqualTo(
                        "Membership cancelled.");

        assertThat(
                occurredAt(entry))
                .isEqualTo(OCCURRED_AT);

        assertThat(
                metadata(entry))
                .containsEntry(
                        "clientId",
                        CLIENT_ID)
                .containsEntry(
                        "membershipPeriodId",
                        PERIOD_ID)
                .containsEntry(
                        "cancelledOn",
                        CANCELLED_ON.toString())
                .containsEntry(
                        "reason",
                        "Client requested cancellation")
                .containsEntry(
                        "previousStatus",
                        MembershipStatus.ACTIVE.name())
                .containsEntry(
                        "resultingStatus",
                        MembershipStatus.CANCELLED.name())
                .containsEntry(
                        "statusChanged",
                        true)
                .containsEntry(
                        "closedOpenFreeze",
                        false);
    }

    @Test
    void shouldMapFrozenMembershipCancellation() {
        MembershipCancelled event =
                frozenMembershipCancellation();

        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(
                        event);

        assertThat(
                actorUserId(entry))
                .isEqualTo(ACTOR_ID);

        assertThat(
                actionCode(entry))
                .isEqualTo(
                        "MEMBERSHIP_CANCELLED");

        assertThat(
                resourceType(entry))
                .isEqualTo(
                        "MEMBERSHIP");

        assertThat(
                resourceId(entry))
                .isEqualTo(MEMBERSHIP_ID);

        assertThat(
                summary(entry))
                .isEqualTo(
                        "Membership cancelled.");

        assertThat(
                metadata(entry))
                .containsEntry(
                        "clientId",
                        CLIENT_ID)
                .containsEntry(
                        "membershipPeriodId",
                        PERIOD_ID)
                .containsEntry(
                        "cancelledOn",
                        CANCELLED_ON.toString())
                .containsEntry(
                        "reason",
                        "Client requested cancellation")
                .containsEntry(
                        "previousStatus",
                        MembershipStatus.FROZEN.name())
                .containsEntry(
                        "resultingStatus",
                        MembershipStatus.CANCELLED.name())
                .containsEntry(
                        "statusChanged",
                        true)
                .containsEntry(
                        "closedOpenFreeze",
                        true);
    }

    @Test
    void shouldStoreCancellationDateAsIsoText() {
        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(
                        activeMembershipCancellation());

        Object cancelledOn =
                metadata(entry)
                        .get("cancelledOn");

        assertThat(cancelledOn)
                .isInstanceOf(String.class)
                .isEqualTo("2026-09-15");
    }

    @Test
    void shouldStoreStatusesAsText() {
        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(
                        activeMembershipCancellation());

        Map<String, Object> metadata =
                metadata(entry);

        assertThat(
                metadata.get(
                        "previousStatus"))
                .isInstanceOf(String.class)
                .isEqualTo("ACTIVE");

        assertThat(
                metadata.get(
                        "resultingStatus"))
                .isInstanceOf(String.class)
                .isEqualTo("CANCELLED");
    }

    @Test
    void shouldGenerateAuditEntryIdentifier() {
        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(
                        activeMembershipCancellation());

        assertThat(
                identifier(entry))
                .isNotNull();
    }

    @Test
    void shouldRejectMissingCancellationEvent() {
        assertThatThrownBy(
                () ->
                        AuditEntryJpaEntity.from(
                                (MembershipCancelled) null))
                .isInstanceOf(
                        IllegalArgumentException.class)
                .hasMessage(
                        "Membership cancelled event "
                                + "must be provided.");
    }

    private static MembershipCancelled
    activeMembershipCancellation() {

        return new MembershipCancelled(
                MEMBERSHIP_ID,
                "MEM-000001",
                CLIENT_ID,
                PERIOD_ID,
                CANCELLED_ON,
                "Client requested cancellation",
                MembershipStatus.ACTIVE,
                MembershipStatus.CANCELLED,
                false,
                ACTOR_ID,
                "coach-admin",
                OCCURRED_AT);
    }

    private static MembershipCancelled
    frozenMembershipCancellation() {

        return new MembershipCancelled(
                MEMBERSHIP_ID,
                "MEM-000001",
                CLIENT_ID,
                PERIOD_ID,
                CANCELLED_ON,
                "Client requested cancellation",
                MembershipStatus.FROZEN,
                MembershipStatus.CANCELLED,
                true,
                ACTOR_ID,
                "coach-admin",
                OCCURRED_AT);
    }

    private static UUID identifier(
            AuditEntryJpaEntity entry) {

        return field(
                entry,
                "id");
    }

    private static UUID actorUserId(
            AuditEntryJpaEntity entry) {

        return field(
                entry,
                "actorUserId");
    }

    private static String actorIdentifier(
            AuditEntryJpaEntity entry) {

        return field(
                entry,
                "actorIdentifierSnapshot");
    }

    private static String actionCode(
            AuditEntryJpaEntity entry) {

        return field(
                entry,
                "actionCode");
    }

    private static String resourceType(
            AuditEntryJpaEntity entry) {

        return field(
                entry,
                "resourceType");
    }

    private static UUID resourceId(
            AuditEntryJpaEntity entry) {

        return field(
                entry,
                "resourceId");
    }

    private static String resourceCode(
            AuditEntryJpaEntity entry) {

        return field(
                entry,
                "resourceCodeSnapshot");
    }

    private static String summary(
            AuditEntryJpaEntity entry) {

        return field(
                entry,
                "summary");
    }

    private static Instant occurredAt(
            AuditEntryJpaEntity entry) {

        return field(
                entry,
                "occurredAt");
    }

    private static Map<String, Object> metadata(
            AuditEntryJpaEntity entry) {

        return field(
                entry,
                "metadata");
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(
            AuditEntryJpaEntity entry,
            String fieldName) {

        return (T)
                ReflectionTestUtils.getField(
                        entry,
                        fieldName);
    }
}
