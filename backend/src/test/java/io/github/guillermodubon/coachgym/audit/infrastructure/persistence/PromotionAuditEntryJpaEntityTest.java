package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.promotion.PromotionChanged;
import io.github.guillermodubon.coachgym.promotion.PromotionChangeType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PromotionAuditEntryJpaEntityTest {

    private static final UUID PROMOTION_ID =
            UUID.fromString(
                    "32f9291d-2263-4099-aac4-a51958ce82c0");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "2d903ea1-b5e6-4506-b72e-7ab905ec5fa6");

    private static final Instant OCCURRED_AT =
            Instant.parse("2026-08-15T04:00:00Z");

    @Test
    void mapsPromotionCreatedEventToAuditEntry() {
        PromotionChanged event =
                new PromotionChanged(
                        PROMOTION_ID,
                        "PROMO-000001",
                        PromotionChangeType.CREATED,
                        ACTOR_ID,
                        "coach-admin",
                        OCCURRED_AT);

        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(event);

        assertThat(entry.id()).isNotNull();

        assertThat(entry.actorUserId())
                .isEqualTo(ACTOR_ID);

        assertThat(entry.actorIdentifierSnapshot())
                .isEqualTo("coach-admin");

        assertThat(entry.actionCode())
                .isEqualTo("PROMOTION_CREATED");

        assertThat(entry.resourceType())
                .isEqualTo("PROMOTION");

        assertThat(entry.resourceId())
                .isEqualTo(PROMOTION_ID);

        assertThat(entry.resourceCodeSnapshot())
                .isEqualTo("PROMO-000001");

        assertThat(entry.summary())
                .isEqualTo("Promotion created.");

        assertThat(entry.metadata())
                .isNotNull()
                .isEmpty();

        assertThat(entry.occurredAt())
                .isEqualTo(OCCURRED_AT);
    }

    @Test
    void mapsPromotionUpdatedEventToAuditEntry() {
        assertAuditMapping(
                PromotionChangeType.UPDATED,
                "PROMOTION_UPDATED",
                "Promotion updated.");
    }

    @Test
    void mapsPromotionDeactivatedEventToAuditEntry() {
        assertAuditMapping(
                PromotionChangeType.DEACTIVATED,
                "PROMOTION_DEACTIVATED",
                "Promotion deactivated.");
    }

    @Test
    void mapsPromotionReactivatedEventToAuditEntry() {
        assertAuditMapping(
                PromotionChangeType.REACTIVATED,
                "PROMOTION_REACTIVATED",
                "Promotion reactivated.");
    }

    @Test
    void mapsEligiblePlansChangedEventToAuditEntry() {
        assertAuditMapping(
                PromotionChangeType.ELIGIBLE_PLANS_CHANGED,
                "PROMOTION_ELIGIBLE_PLANS_CHANGED",
                "Promotion eligible plans changed.");
    }

    private static void assertAuditMapping(
            PromotionChangeType changeType,
            String expectedActionCode,
            String expectedSummary) {

        PromotionChanged event =
                new PromotionChanged(
                        PROMOTION_ID,
                        "PROMO-000001",
                        changeType,
                        ACTOR_ID,
                        "coach-admin",
                        OCCURRED_AT);

        AuditEntryJpaEntity entry =
                AuditEntryJpaEntity.from(event);

        assertThat(entry.id()).isNotNull();

        assertThat(entry.actorUserId())
                .isEqualTo(ACTOR_ID);

        assertThat(entry.actorIdentifierSnapshot())
                .isEqualTo("coach-admin");

        assertThat(entry.actionCode())
                .isEqualTo(expectedActionCode);

        assertThat(entry.resourceType())
                .isEqualTo("PROMOTION");

        assertThat(entry.resourceId())
                .isEqualTo(PROMOTION_ID);

        assertThat(entry.resourceCodeSnapshot())
                .isEqualTo("PROMO-000001");

        assertThat(entry.summary())
                .isEqualTo(expectedSummary);

        assertThat(entry.metadata())
                .isNotNull()
                .isEmpty();

        assertThat(entry.occurredAt())
                .isEqualTo(OCCURRED_AT);
    }
}
