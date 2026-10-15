package io.github.guillermodubon.coachgym.user;

import java.util.Collection;
import java.util.UUID;

/** Allowlisted organization-administrator input for a new invitation. */
public record CreateStaffInvitationCommand(
        String email,
        RoleCode proposedRole,
        StaffScopeType proposedScope,
        Collection<UUID> proposedBranchIds) {

    public CreateStaffInvitationCommand {
        email = StaffIdentityValuePolicy.normalizeEmail(email);
        proposedBranchIds = StaffInvitationPolicy.requireValidProposal(
                proposedRole, proposedScope, proposedBranchIds);
    }

    @Override
    public String toString() {
        return "CreateStaffInvitationCommand[email=" + StaffIdentityValuePolicy.maskEmail(email)
                + ", proposedRole=" + proposedRole
                + ", proposedScope=" + proposedScope
                + ", proposedBranchCount=" + proposedBranchIds.size()
                + ']';
    }
}
