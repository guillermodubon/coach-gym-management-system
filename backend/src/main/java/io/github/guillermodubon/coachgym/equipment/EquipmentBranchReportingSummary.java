package io.github.guillermodubon.coachgym.equipment;

import java.util.Objects;
import java.util.UUID;

/** Branch-attributed equipment snapshot with no equipment details. */
public record EquipmentBranchReportingSummary(UUID branchId, EquipmentReportingSummary metrics) {

    public EquipmentBranchReportingSummary {
        Objects.requireNonNull(branchId, "Equipment reporting branch id is required.");
        Objects.requireNonNull(metrics, "Equipment reporting metrics are required.");
    }
}
