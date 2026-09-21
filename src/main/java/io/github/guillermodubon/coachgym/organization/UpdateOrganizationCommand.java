package io.github.guillermodubon.coachgym.organization;

/**
 * Server-authorized organization update. Code, status, actor, and timestamps
 * are deliberately absent because they are not caller-editable fields.
 */
public record UpdateOrganizationCommand(
        String legalName,
        String brandName,
        String supportEmail,
        String supportPhone,
        String defaultTimezone,
        String defaultCurrency,
        long expectedVersion) {

    public UpdateOrganizationCommand {
        legalName = OrganizationValuePolicy.organizationName(legalName, "Organization legal name");
        brandName = OrganizationValuePolicy.displayName(brandName, "Organization brand name");
        supportEmail = OrganizationValuePolicy.email(supportEmail, "Organization support email");
        supportPhone = OrganizationValuePolicy.phone(supportPhone, "Organization support phone");
        defaultTimezone = OrganizationValuePolicy.timezone(defaultTimezone, false);
        defaultCurrency = OrganizationValuePolicy.currency(defaultCurrency);
        OrganizationValuePolicy.version(expectedVersion, "Expected organization version");
    }
}
