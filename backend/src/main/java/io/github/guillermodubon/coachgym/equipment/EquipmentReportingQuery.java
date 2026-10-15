package io.github.guillermodubon.coachgym.equipment;

import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import java.util.List;

/** Source-owned, read-only aggregates over canonical equipment rows. */
public interface EquipmentReportingQuery {

    EquipmentReportingSummary summarize(ReportingQueryScope scope);

    List<EquipmentBranchReportingSummary> summarizeByBranch(ReportingQueryScope scope);
}
