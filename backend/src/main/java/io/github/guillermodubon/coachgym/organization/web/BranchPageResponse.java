package io.github.guillermodubon.coachgym.organization.web;

import io.github.guillermodubon.coachgym.organization.application.GymBranchPage;
import java.util.List;

/** Bounded branch catalog response with stable pagination metadata. */
public record BranchPageResponse(
        List<BranchResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static BranchPageResponse from(GymBranchPage page) {
        return new BranchPageResponse(
                page.items().stream().map(BranchResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}
