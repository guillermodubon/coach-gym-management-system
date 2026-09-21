package io.github.guillermodubon.coachgym.organization;

import java.util.UUID;

/** Minimal branch projection for future staff assignment and navigation. */
public record GymBranchSummary(
        UUID id,
        UUID organizationId,
        String code,
        String name,
        String timezone,
        GymBranchStatus status,
        boolean initialBranch) {

    public GymBranchSummary {
        if (id == null || organizationId == null) {
            throw new GymBranchValidationException(
                    "Branch and organization identifiers are required.");
        }
        code = OrganizationValuePolicy.code(code, "Branch code");
        name = OrganizationValuePolicy.branchName(name);
        timezone = OrganizationValuePolicy.timezone(timezone, false);
        if (status == null) {
            throw new GymBranchValidationException("Branch status is required.");
        }
    }

    public static GymBranchSummary from(GymBranchDetails details) {
        if (details == null) {
            throw new GymBranchValidationException("Branch details are required.");
        }
        return new GymBranchSummary(
                details.id(),
                details.organizationId(),
                details.code(),
                details.name(),
                details.timezone(),
                details.status(),
                details.initialBranch());
    }
}
