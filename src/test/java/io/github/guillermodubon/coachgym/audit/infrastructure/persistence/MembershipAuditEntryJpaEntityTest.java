package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.membership.MembershipCreated;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MembershipAuditEntryJpaEntityTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "e8ff4202-afdb-43f6-b511-44ac9037675d");

    private static final UUID CLIENT_ID =
            UUID.fromString(
                    "66dd795a-d7c6-4bce-8582-f80ac90dc0e1");

    private static final UUID PERIOD_ID =
            UUID.fromString(
                    "8c92aee4-1fdc-4c33-9792-b6bcb4e03bf1");

    private static final UUID PLAN_ID =
            UUID.fromString(
                    "989c1919-b18a-4dd0-88f2-c28d35850640");

    private static final UUID PROMOTION_ID =
            UUID.fromString(
                    "b531f319-d6b9-4f87-8c5e-8efb328d62d9");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "d58dcc34-f37a-4449-b8b4-1a46bb417ea7");

    private static final Instant NOW =
            Instant.parse(
                    "2026-08-16T21:00:00Z");

    @Test
    void createsMembershipAuditEntryWithPromotionMetadata() {
        MembershipCreated event =
                membershipCreated(
                        PROMOTION_ID);

        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(event);

        assertThat(field(entry, "actorUserId"))
                .isEqualTo(ACTOR_ID);

        assertThat(
                field(
                        entry,
                        "actorIdentifierSnapshot"))
                .isEqualTo("coach-admin");

        assertThat(field(entry, "actionCode"))
                .isEqualTo("MEMBERSHIP_CREATED");

        assertThat(field(entry, "resourceType"))
                .isEqualTo("MEMBERSHIP");

        assertThat(field(entry, "resourceId"))
                .isEqualTo(MEMBERSHIP_ID);

        assertThat(
                field(
                        entry,
                        "resourceCodeSnapshot"))
                .isEqualTo("MEM-000001");

        assertThat(field(entry, "summary"))
                .isEqualTo("Membership created.");

        assertThat(field(entry, "occurredAt"))
                .isEqualTo(NOW);

        Map<String, Object> metadata =
                metadata(entry);

        assertThat(metadata)
                .containsEntry(
                        "clientId",
                        CLIENT_ID.toString())
                .containsEntry(
                        "membershipPeriodId",
                        PERIOD_ID.toString())
                .containsEntry(
                        "membershipPlanId",
                        PLAN_ID.toString())
                .containsEntry(
                        "promotionId",
                        PROMOTION_ID.toString())
                .containsEntry(
                        "listPrice",
                        "25.00")
                .containsEntry(
                        "discountAmount",
                        "2.50")
                .containsEntry(
                        "finalPrice",
                        "22.50")
                .containsEntry(
                        "currency",
                        "USD")
                .containsEntry(
                        "startsOn",
                        "2026-09-01")
                .containsEntry(
                        "effectiveEndsOn",
                        "2026-10-01");
    }

    @Test
    void omitsPromotionMetadataWhenNoPromotionWasApplied() {
        MembershipCreated event =
                new MembershipCreated(
                        MEMBERSHIP_ID,
                        "MEM-000001",
                        CLIENT_ID,
                        PERIOD_ID,
                        PLAN_ID,
                        null,
                        new BigDecimal("25.00"),
                        new BigDecimal("0.00"),
                        new BigDecimal("25.00"),
                        "USD",
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 10, 1),
                        ACTOR_ID,
                        "coach-admin",
                        NOW);

        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(event);

        Map<String, Object> metadata =
                metadata(entry);

        assertThat(metadata)
                .doesNotContainKey("promotionId")
                .containsEntry(
                        "discountAmount",
                        "0.00")
                .containsEntry(
                        "finalPrice",
                        "25.00");
    }

    private static MembershipCreated membershipCreated(
            UUID promotionId) {

        return new MembershipCreated(
                MEMBERSHIP_ID,
                "MEM-000001",
                CLIENT_ID,
                PERIOD_ID,
                PLAN_ID,
                promotionId,
                new BigDecimal("25.00"),
                new BigDecimal("2.50"),
                new BigDecimal("22.50"),
                "USD",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 1),
                ACTOR_ID,
                "coach-admin",
                NOW);
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
