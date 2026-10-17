package io.github.guillermodubon.coachgym.plan.application;

import io.github.guillermodubon.coachgym.plan.PlanDetails;
import java.util.List;

public record PlanPage(
        List<PlanDetails> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public PlanPage {
        items = List.copyOf(items);
    }
}
