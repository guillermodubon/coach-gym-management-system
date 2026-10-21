package io.github.guillermodubon.coachgym.membership.application;

import java.util.UUID;

/** Persistence port for a page of memberships in one already-authorized branch. */
public interface MembershipReadQuery {

    MembershipPage findPage(MembershipSearchQuery query, UUID authorizedBranchId);
}
