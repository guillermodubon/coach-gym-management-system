package io.github.guillermodubon.coachgym.configuration;

import java.util.UUID;

/** Allowlisted organization-admin request to set or clear one branch override. */
public record UpdateBranchAccessPolicyCommand(
        UUID branchId,
        BranchAccessPaymentPolicyMode mode,
        long expectedVersion) {

    public UpdateBranchAccessPolicyCommand {
        if (branchId == null) {
            throw new AccessPaymentPolicyValidationException(
                    "Branch ID is required.");
        }
        if (mode == null) {
            throw new AccessPaymentPolicyValidationException(
                    "Branch policy mode is required.");
        }
        if (expectedVersion < 0) {
            throw new AccessPaymentPolicyValidationException(
                    "Expected branch access-policy version must not be negative.");
        }
    }
}
