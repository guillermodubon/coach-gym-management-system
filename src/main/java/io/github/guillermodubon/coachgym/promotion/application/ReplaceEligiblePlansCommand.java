package io.github.guillermodubon.coachgym.promotion.application;

import java.util.Set;
import java.util.UUID;

public record ReplaceEligiblePlansCommand(
        Set<UUID> planIds,
        long promotionVersion) {

    public ReplaceEligiblePlansCommand {
        if (planIds == null) {
            throw new IllegalArgumentException(
                    "Eligible plan identifiers must be provided.");
        }

        if (planIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Eligible plan identifiers must not contain null.");
        }

        if (promotionVersion < 0) {
            throw new IllegalArgumentException(
                    "Promotion version must not be negative.");
        }

        planIds = Set.copyOf(planIds);
    }
}
