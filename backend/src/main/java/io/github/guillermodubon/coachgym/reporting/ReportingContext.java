package io.github.guillermodubon.coachgym.reporting;

import java.time.Instant;
import java.util.Objects;

/** Applied, safe response context shared by bounded report summaries. */
public record ReportingContext(
        BranchReportingSelection selection,
        ReportingRange range,
        Instant generatedAt) {

    public ReportingContext {
        Objects.requireNonNull(selection, "Applied reporting selection is required.");
        Objects.requireNonNull(range, "Applied reporting range is required.");
        Objects.requireNonNull(generatedAt, "Report generation instant is required.");
    }
}
