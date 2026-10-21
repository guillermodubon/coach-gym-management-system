package io.github.guillermodubon.coachgym.membership.application;

import io.github.guillermodubon.coachgym.membership.MembershipDetails;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only, actor-authorized membership-period history use case. */
@Service
public class MembershipPeriodQueryApplicationService {

    private final MembershipApplicationService membershipService;
    private final MembershipPeriodReadQuery periodReadQuery;

    public MembershipPeriodQueryApplicationService(
            MembershipApplicationService membershipService,
            MembershipPeriodReadQuery periodReadQuery) {
        this.membershipService = Objects.requireNonNull(membershipService);
        this.periodReadQuery = Objects.requireNonNull(periodReadQuery);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public MembershipPeriodPage findPage(
            UUID membershipId,
            MembershipPeriodSearchQuery query,
            AuthenticatedActor actor) {
        Objects.requireNonNull(query, "Membership period query is required.");
        UUID authorizedBranchId = authorizeParent(membershipId, actor);
        return periodReadQuery.findPage(membershipId, authorizedBranchId, query);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public MembershipPeriodReadEntry findById(
            UUID membershipId,
            UUID periodId,
            AuthenticatedActor actor) {
        Objects.requireNonNull(periodId, "Membership period identifier is required.");
        UUID authorizedBranchId = authorizeParent(membershipId, actor);
        return periodReadQuery.findById(membershipId, periodId, authorizedBranchId)
                .orElseThrow(MembershipPeriodNotFoundException::new);
    }

    private UUID authorizeParent(UUID membershipId, AuthenticatedActor actor) {
        Objects.requireNonNull(membershipId, "Membership identifier is required.");
        Objects.requireNonNull(actor, "Authenticated actor is required.");
        MembershipDetails membership = membershipService.findById(membershipId, actor);
        UUID registeredAtBranchId = membership.registeredAtBranchId();
        if (registeredAtBranchId == null) {
            throw new MembershipPeriodQueryUnavailableException(
                    new IllegalStateException("Membership registration branch is missing."));
        }
        return registeredAtBranchId;
    }
}
