package io.github.guillermodubon.coachgym.organization;

/**
 * Request to create a branch under the canonical organization. Organization
 * identity, initial-branch status, lifecycle status, actor, and timestamps are
 * server-owned.
 */
public record CreateGymBranchCommand(
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
        String timezone) {

    public CreateGymBranchCommand {
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
        timezone = OrganizationValuePolicy.timezone(timezone, true);
    }
}
