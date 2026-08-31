package io.github.guillermodubon.coachgym.equipment.web;

import io.github.guillermodubon.coachgym.equipment.application.EquipmentPage;
import java.util.List;

/** Paginated response body for the equipment list endpoint. */
public record EquipmentPageResponse(
        List<EquipmentResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static EquipmentPageResponse from(EquipmentPage page) {
        return new EquipmentPageResponse(
                page.items().stream().map(EquipmentResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}
