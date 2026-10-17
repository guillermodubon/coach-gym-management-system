package io.github.guillermodubon.coachgym.maintenance.web;

import io.github.guillermodubon.coachgym.maintenance.application.IncidentPage;
import java.util.List;

public record IncidentPageResponse(
        List<IncidentResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static IncidentPageResponse from(IncidentPage incidentPage) {
        return new IncidentPageResponse(
                incidentPage.items().stream()
                        .map(IncidentResponse::from)
                        .toList(),
                incidentPage.page(), incidentPage.size(),
                incidentPage.totalElements(), incidentPage.totalPages());
    }
}
