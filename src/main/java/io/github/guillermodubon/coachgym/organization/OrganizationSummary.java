package io.github.guillermodubon.coachgym.organization;

import java.util.UUID;

/** Minimal organization projection safe for cross-module and frontend use. */
public record OrganizationSummary(
        UUID id,
        String code,
        String brandName,
        String defaultTimezone,
        String defaultCurrency,
        OrganizationStatus status) {

    public OrganizationSummary {
        if (id == null) {
            throw new OrganizationValidationException("Organization id is required.");
        }
        code = OrganizationValuePolicy.code(code, "Organization code");
        brandName = OrganizationValuePolicy.displayName(brandName, "Organization brand name");
        defaultTimezone = OrganizationValuePolicy.timezone(defaultTimezone, false);
        defaultCurrency = OrganizationValuePolicy.currency(defaultCurrency);
        if (status == null) {
            throw new OrganizationValidationException("Organization status is required.");
        }
    }

    public static OrganizationSummary from(OrganizationDetails details) {
        if (details == null) {
            throw new OrganizationValidationException("Organization details are required.");
        }
        return new OrganizationSummary(
                details.id(),
                details.code(),
                details.brandName(),
                details.defaultTimezone(),
                details.defaultCurrency(),
                details.status());
    }
}
