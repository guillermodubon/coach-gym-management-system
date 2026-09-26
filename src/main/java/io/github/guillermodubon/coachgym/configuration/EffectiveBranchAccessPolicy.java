package io.github.guillermodubon.coachgym.configuration;

import java.util.UUID;

/**
 * Technology-neutral payment policy resolved for one physical branch.
 * {@code INHERIT} always follows the organization default; it is not equivalent
 * to {@code NOT_REQUIRED}.
 */
public record EffectiveBranchAccessPolicy(
        UUID organizationId,
        UUID branchId,
        boolean organizationDefaultRequiresConfirmedPayment,
        BranchAccessPaymentPolicyMode branchMode,
        long version) {

    public EffectiveBranchAccessPolicy {
        if (organizationId == null || branchId == null) {
            throw new AccessPaymentPolicyValidationException(
                    "Organization and branch IDs are required.");
        }
        if (branchMode == null) {
            throw new AccessPaymentPolicyValidationException(
                    "Branch access-payment policy mode is required.");
        }
        if (version < 0) {
            throw new AccessPaymentPolicyValidationException(
                    "Branch access-payment policy version must not be negative.");
        }
    }

    /** Compatibility constructor for an unversioned policy value. */
    public EffectiveBranchAccessPolicy(
            UUID organizationId,
            UUID branchId,
            boolean organizationDefaultRequiresConfirmedPayment,
            BranchAccessPaymentPolicyMode branchMode) {
        this(organizationId, branchId,
                organizationDefaultRequiresConfirmedPayment, branchMode, 0);
    }

    /** Returns the requirement that applies to access at this branch. */
    public boolean requireConfirmedPaymentForAccess() {
        return switch (branchMode) {
            case INHERIT -> organizationDefaultRequiresConfirmedPayment;
            case REQUIRED -> true;
            case NOT_REQUIRED -> false;
        };
    }
}
