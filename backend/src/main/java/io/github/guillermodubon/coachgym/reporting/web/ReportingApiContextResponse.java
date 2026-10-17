package io.github.guillermodubon.coachgym.reporting.web;

import io.github.guillermodubon.coachgym.reporting.ReportingContext;
import io.github.guillermodubon.coachgym.reporting.ReportingScope;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Applied authorization scope and half-open calendar range for a report. */
public record ReportingApiContextResponse(
        ReportingScope scope,
        List<UUID> branchIds,
        LocalDate fromInclusive,
        LocalDate toExclusive,
        String timezone,
        Instant generatedAt) {

    public ReportingApiContextResponse {
        branchIds = List.copyOf(branchIds);
    }

    static ReportingApiContextResponse from(ReportingContext context) {
        return new ReportingApiContextResponse(
                context.selection().scope(),
                context.selection().branchIds(),
                context.range().fromInclusive(),
                context.range().toExclusive(),
                context.range().timezone().getId(),
                context.generatedAt());
    }
}
