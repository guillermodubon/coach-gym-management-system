package io.github.guillermodubon.coachgym.user;

import java.util.Objects;
import java.util.UUID;

/**
 * Pure branch authorization rules shared by operational modules.
 *
 * <p>The policy distinguishes ordinary active-context operations from
 * explicit organization-administrator addressing. It does not load state,
 * perform persistence, or decide operation-specific roles.</p>
 */
public final class BranchResourceAuthorizationPolicy {

    private BranchResourceAuthorizationPolicy() {
    }

    /** Returns the selected active branch or rejects a missing/stale context. */
    public static UUID requireActiveBranch(BranchOperationContext context) {
        Objects.requireNonNull(context, "context is required");
        if (!context.hasActiveBranch()) {
            throw new ActiveBranchContextUnavailableException();
        }
        return context.activeBranchId();
    }

    /**
     * Resolves the server-owned creation branch and rejects conflicting input.
     * A caller-supplied branch is never accepted as authority.
     */
    public static UUID requireCreationBranch(
            BranchOperationContext context,
            UUID requestedBranchId) {
        UUID activeBranchId = requireActiveBranch(context);
        if (requestedBranchId != null && !activeBranchId.equals(requestedBranchId)) {
            throw new BranchResourceMismatchException();
        }
        return activeBranchId;
    }

    /**
     * Resolves the branch scope for a bounded operational list query.
     *
     * <p>Organization-scoped administrators may explicitly select an active
     * branch that belongs to their authorized branch projection. Otherwise the
     * current active branch is mandatory. A missing filter never means
     * "search every branch", and a branch-scoped caller cannot use a filter to
     * switch away from the active branch.</p>
     */
    public static UUID requireListBranch(
            BranchOperationContext context,
            UUID requestedBranchId) {
        Objects.requireNonNull(context, "context is required");
        if (requestedBranchId == null) {
            return requireActiveBranch(context);
        }
        if (context.organizationWide()
                && context.canAddressAuthorizedBranch(requestedBranchId)) {
            return requestedBranchId;
        }
        if (context.hasActiveBranch()
                && context.activeBranchId().equals(requestedBranchId)) {
            return requestedBranchId;
        }
        throw new BranchResourceAuthorizationException();
    }

    /** Requires an ordinary detail, lifecycle, mutation, or download operation. */
    public static void requireActiveResourceAccess(
            BranchOperationContext context,
            BranchOwnedResourceReference resource) {
        Objects.requireNonNull(resource, "resource is required");
        UUID activeBranchId = requireActiveBranch(context);
        requireOrganization(context, resource);
        if (!activeBranchId.equals(resource.branchId())) {
            throw new BranchResourceAuthorizationException();
        }
    }

    /**
     * Requires an explicit organization-administrator target for an approved
     * cross-branch administrative operation.
     */
    public static void requireOrganizationResourceAccess(
            BranchOperationContext context,
            BranchOwnedResourceReference resource) {
        Objects.requireNonNull(context, "context is required");
        Objects.requireNonNull(resource, "resource is required");
        if (!context.organizationWide() || !context.canAddressAuthorizedBranch(resource.branchId())) {
            throw new BranchResourceAuthorizationException();
        }
        requireOrganization(context, resource);
    }

    private static void requireOrganization(
            BranchOperationContext context,
            BranchOwnedResourceReference resource) {
        if (!context.organizationId().equals(resource.organizationId())) {
            throw new BranchResourceAuthorizationException();
        }
    }
}
