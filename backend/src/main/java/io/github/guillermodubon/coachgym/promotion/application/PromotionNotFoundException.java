package io.github.guillermodubon.coachgym.promotion.application;

import java.util.UUID;

public class PromotionNotFoundException extends RuntimeException {

    public PromotionNotFoundException(UUID id) {
        super("Promotion " + id + " was not found.");
    }
}

