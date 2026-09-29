package io.github.guillermodubon.coachgym.notification;

import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;
import java.util.List;

/** Read-only source-owned aggregates over the durable transactional email outbox. */
public interface EmailDeliveryReportingQuery {

    EmailDeliveryReportingSummary summarize(
            ReportingQueryScope scope,
            ReportingQueryWindow window);

    List<EmailDeliveryBranchReportingSummary> summarizeByBranch(
            ReportingQueryScope scope,
            ReportingQueryWindow window);
}
