package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Internal, already-authorized data used to provision one accepted identity atomically. */
public record StaffAccountProvisioningDraft(
        UUID userId,
        String email,
        String passwordHash,
        String firstName,
        String lastName,
        RoleCode role,
        StaffScopeType scope,
        Set<UUID> branchIds,
        UUID invitedByUserId,
        Instant provisionedAt) {

    public StaffAccountProvisioningDraft {
        if (userId == null || email == null || passwordHash == null || passwordHash.isBlank()
                || firstName == null || lastName == null || role == null || scope == null
                || invitedByUserId == null || provisionedAt == null) {
            throw new IllegalArgumentException("Staff account provisioning data is incomplete.");
        }
        branchIds = branchIds == null ? Set.of() : Set.copyOf(branchIds);
        if (branchIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Staff account branch proposal is invalid.");
        }
    }

    @Override
    public String toString() {
        return "StaffAccountProvisioningDraft[userId=" + userId
                + ", email=<redacted>, passwordHash=<redacted>"
                + ", firstNamePresent=" + !firstName.isBlank()
                + ", lastNamePresent=" + !lastName.isBlank()
                + ", role=" + role
                + ", scope=" + scope
                + ", branchCount=" + branchIds.size()
                + ", invitedByUserId=" + invitedByUserId
                + ", provisionedAt=" + provisionedAt + ']';
    }
}
