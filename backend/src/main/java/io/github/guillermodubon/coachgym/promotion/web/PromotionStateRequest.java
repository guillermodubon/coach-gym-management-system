package io.github.guillermodubon.coachgym.promotion.web;

import jakarta.validation.constraints.PositiveOrZero;

public record PromotionStateRequest(
        @PositiveOrZero
        long version) {
}
