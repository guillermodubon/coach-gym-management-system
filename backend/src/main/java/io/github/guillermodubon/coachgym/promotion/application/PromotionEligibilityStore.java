package io.github.guillermodubon.coachgym.promotion.application;

import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface PromotionEligibilityStore {

    Set<UUID> findEligiblePlanIds(UUID promotionId);

    boolean isPlanEligible(UUID promotionId, UUID membershipPlanId);

    Set<UUID> replaceEligiblePlanIds(
            UUID promotionId,
            Set<UUID> planIds,
            long expectedPromotionVersion,
            AuthenticatedActor actor,
            Instant occurredAt
    );
}

