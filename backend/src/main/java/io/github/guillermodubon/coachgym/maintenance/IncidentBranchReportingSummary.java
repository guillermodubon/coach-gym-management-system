package io.github.guillermodubon.coachgym.maintenance;

import java.util.Objects;
import java.util.UUID;

/** Branch-attributed incident aggregate with no personal or narrative data. */
public record IncidentBranchReportingSummary(UUID branchId, IncidentReportingSummary metrics) {

    public IncidentBranchReportingSummary {
        Objects.requireNonNull(branchId, "Incident reporting branch id is required.");
        Objects.requireNonNull(metrics, "Incident reporting metrics are required.");
    }
}
