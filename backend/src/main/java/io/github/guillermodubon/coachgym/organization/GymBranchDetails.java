package io.github.guillermodubon.coachgym.organization;

import java.time.Instant;
import java.util.UUID;

/** Immutable detailed projection of one physical gym branch. */
public record GymBranchDetails(
        UUID id,
        UUID organizationId,
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
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public GymBranchDetails {
        if (id == null || organizationId == null) {
            throw new GymBranchValidationException(
                    "Branch and organization identifiers are required.");
        }
        code = OrganizationValuePolicy.code(code, "Branch code");
        name = OrganizationValuePolicy.branchName(name);
        addressLine1 = OrganizationValuePolicy.optionalText(
                addressLine1, "Branch address line 1", OrganizationValuePolicy.MAX_ADDRESS_LENGTH);
        addressLine2 = OrganizationValuePolicy.optionalText(
                addressLine2, "Branch address line 2", OrganizationValuePolicy.MAX_ADDRESS_LENGTH);
        city = OrganizationValuePolicy.optionalText(
                city, "Branch city", OrganizationValuePolicy.MAX_CITY_LENGTH);
        stateOrDepartment = OrganizationValuePolicy.optionalText(
                stateOrDepartment, "Branch state or department", OrganizationValuePolicy.MAX_STATE_LENGTH);
        postalCode = OrganizationValuePolicy.optionalText(
                postalCode, "Branch postal code", OrganizationValuePolicy.MAX_POSTAL_CODE_LENGTH);
        countryCode = OrganizationValuePolicy.countryCode(countryCode);
        phone = OrganizationValuePolicy.phone(phone, "Branch phone");
        email = OrganizationValuePolicy.email(email, "Branch email");
        timezone = OrganizationValuePolicy.timezone(timezone, false);
        if (status == null) {
            throw new GymBranchValidationException("Branch status is required.");
        }
        if (createdAt == null || updatedAt == null) {
            throw new GymBranchValidationException("Branch timestamps are required.");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new GymBranchValidationException(
                    "Branch update timestamp must not precede creation.");
        }
        OrganizationValuePolicy.version(version, "Branch version");
    }

    public boolean isInitialBranch() {
        return initialBranch;
    }
}
