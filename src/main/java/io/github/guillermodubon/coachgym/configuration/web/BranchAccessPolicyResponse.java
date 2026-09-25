package io.github.guillermodubon.coachgym.configuration.web;

import io.github.guillermodubon.coachgym.configuration.EffectiveBranchAccessPolicy;
import io.github.guillermodubon.coachgym.configuration.BranchAccessPaymentPolicyMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Minimal operational view of the policy that applies at one physical branch. */
@Schema(
        name = "BranchAccessPolicyResponse",
        description = "Exposes only the effective confirmed-payment requirement and concurrency version.")
record BranchAccessPolicyResponse(
        @Schema(description = "Branch addressed by the request.")
        UUID branchId,

        @Schema(description = "Branch override mode. INHERIT follows the current organization default.")
        BranchAccessPaymentPolicyMode branchMode,

        @Schema(description = "Whether a confirmed PAID payment is required for access at this branch.")
        boolean requireConfirmedPaymentForAccess,

        @Schema(description = "Current optimistic-lock version; only organization administrators may mutate the override.", minimum = "0")
        long version) {

    static BranchAccessPolicyResponse from(EffectiveBranchAccessPolicy policy) {
        return new BranchAccessPolicyResponse(
                policy.branchId(),
                policy.branchMode(),
                policy.requireConfirmedPaymentForAccess(),
                policy.version());
    }
}
