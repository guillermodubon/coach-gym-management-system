package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.membership.MembershipRenewed;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MembershipRenewalAuditEntryJpaEntityTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "1163e498-9d37-417e-8143-d22fb98215c0");

    private static final UUID CLIENT_ID =
            UUID.fromString(
                    "812544b2-a985-41c4-b107-c36fc54f5c52");

    private static final UUID PERIOD_ID =
            UUID.fromString(
                    "8cb8344f-6739-48e3-8822-cb12ea647808");

    private static final UUID PLAN_ID =
            UUID.fromString(
                    "53457619-c749-408b-ae34-50d20295f260");

    private static final UUID PROMOTION_ID =
            UUID.fromString(
                    "91954536-39d3-40a0-b8b8-c77cedaa3c1a");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "05706da8-33b7-4489-a469-7f65d23329c7");

    private static final Instant NOW =
            Instant.parse(
                    "2026-10-01T14:00:00Z");

    @Test
    void createsRenewalAuditEntryWithoutStatusChange() {
        MembershipRenewed event =
                membershipRenewed(
                        PROMOTION_ID,
                        MembershipStatus.ACTIVE,
                        MembershipStatus.ACTIVE);

        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(
                        event);

        assertThat(field(entry, "actorUserId"))
                .isEqualTo(ACTOR_ID);

        assertThat(
                field(
                        entry,
                        "actorIdentifierSnapshot"))
                .isEqualTo("coach-admin");

        assertThat(field(entry, "actionCode"))
                .isEqualTo("MEMBERSHIP_RENEWED");

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
                .isEqualTo("Membership renewed.");

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
                        "periodNumber",
                        (short) 2)
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
                        "2026-10-01")
                .containsEntry(
                        "effectiveEndsOn",
                        "2026-11-01")
                .containsEntry(
                        "previousStatus",
                        "ACTIVE")
                .containsEntry(
                        "resultingStatus",
                        "ACTIVE")
                .containsEntry(
                        "statusChanged",
                        false);
    }

    @Test
    void recordsExpiredToActiveStatusChange() {
        MembershipRenewed event =
                membershipRenewedWithoutPromotion(
                        MembershipStatus.EXPIRED,
                        MembershipStatus.ACTIVE);

        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(
                        event);

        Map<String, Object> metadata =
                metadata(entry);

        assertThat(metadata)
                .doesNotContainKey(
                        "promotionId")
                .containsEntry(
                        "previousStatus",
                        "EXPIRED")
                .containsEntry(
                        "resultingStatus",
                        "ACTIVE")
                .containsEntry(
                        "statusChanged",
                        true)
                .containsEntry(
                        "listPrice",
                        "25.00")
                .containsEntry(
                        "discountAmount",
                        "0.00")
                .containsEntry(
                        "finalPrice",
                        "25.00")
                .containsEntry(
                        "currency",
                        "USD");
    }

    private static MembershipRenewed membershipRenewed(
            UUID promotionId,
            MembershipStatus previousStatus,
            MembershipStatus resultingStatus) {

        return new MembershipRenewed(
                MEMBERSHIP_ID,
                "MEM-000001",
                CLIENT_ID,
                PERIOD_ID,
                (short) 2,
                PLAN_ID,
                promotionId,
                new BigDecimal("25.00"),
                new BigDecimal("2.50"),
                new BigDecimal("22.50"),
                "USD",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 1),
                previousStatus,
                resultingStatus,
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

    private static MembershipRenewed
    membershipRenewedWithoutPromotion(
            MembershipStatus previousStatus,
            MembershipStatus resultingStatus) {

        return new MembershipRenewed(
                MEMBERSHIP_ID,
                "MEM-000001",
                CLIENT_ID,
                PERIOD_ID,
                (short) 2,
                PLAN_ID,
                null,
                new BigDecimal("25.00"),
                new BigDecimal("0.00"),
                new BigDecimal("25.00"),
                "USD",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 1),
                previousStatus,
                resultingStatus,
                ACTOR_ID,
                "coach-admin",
                NOW);
    }
}
