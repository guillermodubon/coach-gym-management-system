package io.github.guillermodubon.coachgym.client;

import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;

/** Source-owned read port for client counts without client-level details. */
public interface ClientReportingQuery {

    ClientReportingSummary summarize(
            ReportingQueryScope scope,
            ReportingQueryWindow window);
}
