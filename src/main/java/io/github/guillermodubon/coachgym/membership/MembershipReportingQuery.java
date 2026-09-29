package io.github.guillermodubon.coachgym.membership;

import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;
import java.time.LocalDate;

/** Source-owned read port for bounded membership, period, plan, and coverage aggregates. */
public interface MembershipReportingQuery {

    MembershipReportingSummary summarize(
            ReportingQueryScope scope,
            ReportingQueryWindow window,
            LocalDate asOfDate);
}
