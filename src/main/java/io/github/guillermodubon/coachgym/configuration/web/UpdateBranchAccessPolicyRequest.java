package io.github.guillermodubon.coachgym.configuration.web;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyValidationException;
import io.github.guillermodubon.coachgym.configuration.BranchAccessPaymentPolicyMode;
import io.github.guillermodubon.coachgym.configuration.UpdateBranchAccessPolicyCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Allowlisted branch-policy mutation; actor and branch authority come from the server. */
@Schema(
        name = "UpdateBranchAccessPolicyRequest",
        description = "Only organization administrators can change the branch override.")
record UpdateBranchAccessPolicyRequest(
        @NotNull
        @Schema(description = "INHERIT clears the override and follows the organization default; REQUIRED and NOT_REQUIRED override it.")
        BranchAccessPaymentPolicyMode mode,

        @Min(0)
        @Schema(description = "Expected policy version for optimistic locking.", minimum = "0")
        long expectedVersion) {

    UpdateBranchAccessPolicyCommand toCommand(UUID branchId) {
        if (mode == null) {
            throw new AccessPaymentPolicyValidationException(
                    "Branch policy mode is required.");
        }
        return new UpdateBranchAccessPolicyCommand(branchId, mode, expectedVersion);
    }
}
