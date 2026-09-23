package io.github.guillermodubon.coachgym.user;

import java.util.Objects;
import java.util.UUID;

/** Minimal persisted ownership reference shared by branch-aware use cases. */
public record BranchOwnedResourceReference(
        UUID resourceId,
        UUID organizationId,
        UUID branchId) {

    public BranchOwnedResourceReference {
        resourceId = requireId(resourceId, "resourceId");
        organizationId = requireId(organizationId, "organizationId");
        branchId = requireId(branchId, "branchId");
    }

    private static UUID requireId(UUID value, String name) {
        return Objects.requireNonNull(value, name + " is required");
    }
}
