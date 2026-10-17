package io.github.guillermodubon.coachgym.access;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Physical-branch access aggregate; branch names and personal data are intentionally absent. */
public record AccessBranchReportingSummary(
        UUID branchId,
        long totalAttempts,
        long allowedAttempts,
        long deniedAttempts,
        BigDecimal allowedRate,
        long manualAttempts,
        long qrAttempts,
        long unknownSourceAttempts) {

    public AccessBranchReportingSummary {
        Objects.requireNonNull(branchId, "Access reporting branch id is required.");
        Objects.requireNonNull(allowedRate, "Allowed access rate is required.");
        if (totalAttempts < 0 || allowedAttempts < 0 || deniedAttempts < 0
                || manualAttempts < 0 || qrAttempts < 0 || unknownSourceAttempts < 0
                || allowedAttempts + deniedAttempts != totalAttempts
                || manualAttempts + qrAttempts + unknownSourceAttempts != totalAttempts
                || allowedRate.signum() < 0 || allowedRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Branch access metrics must be non-negative and reconcile.");
        }
    }
}
