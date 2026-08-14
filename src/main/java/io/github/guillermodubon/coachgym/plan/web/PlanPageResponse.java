package io.github.guillermodubon.coachgym.plan.web;

import io.github.guillermodubon.coachgym.plan.application.PlanPage;
import java.util.List;

public record PlanPageResponse(
        List<PlanResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static PlanPageResponse from(PlanPage page) {
        return new PlanPageResponse(
                page.items().stream().map(PlanResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}
