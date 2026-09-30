package io.github.guillermodubon.coachgym.reporting;

import java.util.UUID;

/** Safe branch labels included only after the branch set has been authorized. */
public record ReportingBranchIdentity(
        UUID branchId,
        String branchCode,
        String branchName,
        boolean active) {

    public ReportingBranchIdentity {
        if (branchId == null || branchCode == null || branchCode.isBlank()
                || branchName == null || branchName.isBlank()) {
            throw new IllegalArgumentException("Reporting branch identity is invalid.");
        }
        branchCode = branchCode.strip();
        branchName = branchName.strip();
    }
}
