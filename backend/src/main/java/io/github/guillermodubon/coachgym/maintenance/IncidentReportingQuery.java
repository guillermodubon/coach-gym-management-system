package io.github.guillermodubon.coachgym.maintenance;

import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;
import java.util.List;

/** Source-owned, read-only aggregates over canonical incident rows. */
public interface IncidentReportingQuery {

    IncidentReportingSummary summarize(ReportingQueryScope scope, ReportingQueryWindow window);

    List<IncidentBranchReportingSummary> summarizeByBranch(
            ReportingQueryScope scope,
            ReportingQueryWindow window);
}
