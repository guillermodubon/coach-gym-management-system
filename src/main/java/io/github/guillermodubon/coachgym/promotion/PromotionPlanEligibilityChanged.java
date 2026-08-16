package io.github.guillermodubon.coachgym.promotion;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Event emitted after the eligible-plan configuration of a promotion
 * has been replaced successfully.
 */
public record PromotionPlanEligibilityChanged(
        UUID promotionId,
        String promotionCode,
        Set<UUID> eligiblePlanIds,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {

    public PromotionPlanEligibilityChanged {
        eligiblePlanIds =
                Set.copyOf(eligiblePlanIds);
    }
}
