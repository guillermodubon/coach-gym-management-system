package io.github.guillermodubon.coachgym.membership.application;

import java.util.Optional;
import java.util.UUID;

/** Persistence read port restricted to an already-authorized membership branch. */
public interface MembershipPeriodReadQuery {

    MembershipPeriodPage findPage(
            UUID membershipId,
            UUID authorizedBranchId,
            MembershipPeriodSearchQuery query);

    Optional<MembershipPeriodReadEntry> findById(
            UUID membershipId,
            UUID periodId,
            UUID authorizedBranchId);
}
