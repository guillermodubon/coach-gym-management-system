package io.github.guillermodubon.coachgym.organization;

/**
 * Pure lifecycle policy for the single-organization, multi-branch model.
 * Database state and authorization are intentionally outside this policy.
 */
public final class OrganizationBranchLifecyclePolicy {

    /**
     * Validates an organization status transition.
     *
     * <p>The {@code onlyOrganization} flag keeps this policy future-compatible
     * without introducing multi-tenant behavior. The current product always
     * passes {@code true} for its canonical organization.</p>
     */
    public void requireOrganizationTransition(
            OrganizationStatus current,
            OrganizationStatus requested,
            boolean onlyOrganization) {
        if (current == null || requested == null) {
            throw new OrganizationValidationException("Organization statuses are required.");
        }
        if (current == requested) {
            throw new OrganizationStateConflictException(
                    "Organization is already in the requested status.");
        }
        if (requested == OrganizationStatus.INACTIVE && onlyOrganization) {
            throw new OrganizationStateConflictException(
                    "The only canonical organization cannot be deactivated.");
        }
    }

    /**
     * Validates a branch status transition while protecting the initial branch
     * when it is the last active branch.
     */
    public void requireBranchTransition(
            GymBranchStatus current,
            GymBranchStatus requested,
            boolean initialBranch,
            boolean anotherActiveBranchExists) {
        if (current == null || requested == null) {
            throw new GymBranchValidationException("Branch statuses are required.");
        }
        if (current == requested) {
            throw new GymBranchStateConflictException(
                    "Branch is already in the requested status.");
        }
        if (requested == GymBranchStatus.INACTIVE
                && initialBranch
                && !anotherActiveBranchExists) {
            throw new GymBranchStateConflictException(
                    "The initial branch cannot be deactivated while it is the only active branch.");
        }
    }
}
