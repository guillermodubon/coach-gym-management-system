package io.github.guillermodubon.coachgym.reporting.web;

import io.github.guillermodubon.coachgym.reporting.EquipmentDashboardDetails;

public record EquipmentDashboardResponse(long available, long inMaintenance, long outOfService) {
    static EquipmentDashboardResponse from(EquipmentDashboardDetails details) {
        return new EquipmentDashboardResponse(
                details.available(), details.inMaintenance(), details.outOfService());
    }
}
