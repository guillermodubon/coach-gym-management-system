package io.github.guillermodubon.coachgym.equipment.web;

import io.github.guillermodubon.coachgym.equipment.application.EquipmentCategoryPage;
import java.util.List;

/** Paginated response body for equipment category list. */
public record EquipmentCategoryPageResponse(
        List<EquipmentCategoryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static EquipmentCategoryPageResponse from(EquipmentCategoryPage page) {
        return new EquipmentCategoryPageResponse(
                page.items().stream().map(EquipmentCategoryResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}
