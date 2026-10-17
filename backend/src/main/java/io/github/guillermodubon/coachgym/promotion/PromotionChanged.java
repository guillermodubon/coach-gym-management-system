package io.github.guillermodubon.coachgym.promotion;

import java.time.Instant;
import java.util.UUID;

/**
 * Application event emitted after a promotion mutation has been persisted.
 */
public record PromotionChanged(
        UUID promotionId,
        String promotionCode,
        PromotionChangeType changeType,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {

    public PromotionChanged {
        if (promotionId == null) {
            throw new IllegalArgumentException(
                    "Promotion id must be provided.");
        }

        if (promotionCode == null || promotionCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Promotion code must be provided.");
        }

        if (changeType == null) {
            throw new IllegalArgumentException(
                    "Promotion change type must be provided.");
        }

        if (actorUserId == null) {
            throw new IllegalArgumentException(
                    "Actor user id must be provided.");
        }

        if (actorIdentifier == null || actorIdentifier.isBlank()) {
            throw new IllegalArgumentException(
                    "Actor identifier must be provided.");
        }

        if (occurredAt == null) {
            throw new IllegalArgumentException(
                    "Occurrence date must be provided.");
        }
    }
}
