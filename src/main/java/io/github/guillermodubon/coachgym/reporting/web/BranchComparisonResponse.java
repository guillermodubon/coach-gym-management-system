package io.github.guillermodubon.coachgym.reporting.web;

import io.github.guillermodubon.coachgym.reporting.BranchComparisonReport;
import io.github.guillermodubon.coachgym.reporting.BranchComparisonRow;
import java.util.List;

/** Authorized branch comparison response. */
public record BranchComparisonResponse(
        ReportingApiContextResponse context,
        List<BranchComparisonRow> branches) {

    public BranchComparisonResponse {
        branches = List.copyOf(branches);
    }

    static BranchComparisonResponse from(BranchComparisonReport report) {
        return new BranchComparisonResponse(
                ReportingApiContextResponse.from(report.context()), report.branches());
    }
}
