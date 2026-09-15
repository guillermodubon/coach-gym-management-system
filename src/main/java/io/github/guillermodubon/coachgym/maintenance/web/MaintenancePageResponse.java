package io.github.guillermodubon.coachgym.maintenance.web;

import io.github.guillermodubon.coachgym.maintenance.application.MaintenancePage;
import java.util.List;

public record MaintenancePageResponse(
        List<MaintenanceResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static MaintenancePageResponse from(MaintenancePage page) {
        return new MaintenancePageResponse(
                page.items().stream().map(MaintenanceResponse::from).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
