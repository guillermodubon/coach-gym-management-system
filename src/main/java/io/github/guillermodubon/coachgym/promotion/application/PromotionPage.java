package io.github.guillermodubon.coachgym.promotion.application;

import io.github.guillermodubon.coachgym.promotion.PromotionDetails;
import java.util.List;

public record PromotionPage(
        List<PromotionDetails> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public PromotionPage {
        items = List.copyOf(items);
    }
}
