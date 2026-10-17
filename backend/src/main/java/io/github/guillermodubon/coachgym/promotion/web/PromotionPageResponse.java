package io.github.guillermodubon.coachgym.promotion.web;

import io.github.guillermodubon.coachgym.promotion.application.PromotionPage;
import java.util.List;

public record PromotionPageResponse(
        List<PromotionResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public PromotionPageResponse {
        items = List.copyOf(items);
    }

    static PromotionPageResponse from(
            PromotionPage page) {

        return new PromotionPageResponse(
                page.items()
                        .stream()
                        .map(PromotionResponse::from)
                        .toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}
