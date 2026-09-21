package io.github.guillermodubon.coachgym.organization.web;

import io.github.guillermodubon.coachgym.organization.GymBranchDetails;
import io.github.guillermodubon.coachgym.organization.GymBranchStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** HTTP projection for one canonical gym branch. */
@Schema(description = "Canonical gym branch detail without future assignment or tenant fields.")
public record BranchResponse(
        UUID id,
        String code,
        String name,
        String addressLine1,
        String addressLine2,
        String city,
        String stateOrDepartment,
        String postalCode,
        String countryCode,
        String phone,
        String email,
        String timezone,
        GymBranchStatus status,
        boolean initialBranch,
        long version,
        Instant updatedAt) {

    static BranchResponse from(GymBranchDetails details) {
        return new BranchResponse(
                details.id(),
                details.code(),
                details.name(),
                details.addressLine1(),
                details.addressLine2(),
                details.city(),
                details.stateOrDepartment(),
                details.postalCode(),
                details.countryCode(),
                details.phone(),
                details.email(),
                details.timezone(),
                details.status(),
                details.initialBranch(),
                details.version(),
                details.updatedAt());
    }
}
