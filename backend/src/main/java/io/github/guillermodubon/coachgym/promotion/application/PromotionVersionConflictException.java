package io.github.guillermodubon.coachgym.promotion.application;

import java.util.UUID;

public class PromotionVersionConflictException
        extends RuntimeException {

    public PromotionVersionConflictException(
            UUID promotionId,
            long expectedVersion,
            long currentVersion) {

        super(
                "Promotion "
                        + promotionId
                        + " was modified by another operation. "
                        + "Expected version "
                        + expectedVersion
                        + " but found "
                        + currentVersion
                        + ".");
    }
}
