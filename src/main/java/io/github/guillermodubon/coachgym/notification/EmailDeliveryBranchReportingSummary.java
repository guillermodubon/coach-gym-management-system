package io.github.guillermodubon.coachgym.notification;

import java.util.Objects;
import java.util.UUID;

/** Durable-email reporting snapshot for one already-authorized physical branch. */
public record EmailDeliveryBranchReportingSummary(
        UUID branchId,
        EmailDeliveryReportingSummary summary) {

    public EmailDeliveryBranchReportingSummary {
        Objects.requireNonNull(branchId, "Email reporting branch identifier is required.");
        Objects.requireNonNull(summary, "Email reporting summary is required.");
    }
}
