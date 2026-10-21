package io.github.guillermodubon.coachgym.membership.application;

import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.BranchOperationContextResolver;
import io.github.guillermodubon.coachgym.user.BranchResourceAuthorizationPolicy;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only, branch-scoped membership list use case. */
@Service
public class MembershipQueryApplicationService {

    private final BranchOperationContextResolver branchContextResolver;
    private final MembershipReadQuery membershipReadQuery;

    public MembershipQueryApplicationService(
            BranchOperationContextResolver branchContextResolver,
            MembershipReadQuery membershipReadQuery) {
        this.branchContextResolver = Objects.requireNonNull(branchContextResolver);
        this.membershipReadQuery = Objects.requireNonNull(membershipReadQuery);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public MembershipPage findPage(
            MembershipSearchQuery query,
            AuthenticatedActor actor) {
        Objects.requireNonNull(query, "Membership search query is required.");
        Objects.requireNonNull(actor, "Authenticated actor is required.");
        if (actor.id() == null) {
            throw new IllegalArgumentException("Authenticated actor ID is required.");
        }
        var context = branchContextResolver.resolveOperation(actor.id());
        var branchId = BranchResourceAuthorizationPolicy.requireListBranch(
                context, query.branchId());
        return membershipReadQuery.findPage(query, branchId);
    }
}
