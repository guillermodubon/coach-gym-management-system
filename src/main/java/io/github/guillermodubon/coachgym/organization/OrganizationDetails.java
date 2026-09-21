package io.github.guillermodubon.coachgym.organization;

import java.time.Instant;
import java.util.UUID;

/** Immutable canonical organization projection. */
public record OrganizationDetails(
        UUID id,
        String code,
        String legalName,
        String brandName,
        String supportEmail,
        String supportPhone,
        String defaultTimezone,
        String defaultCurrency,
        OrganizationStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public OrganizationDetails {
        if (id == null) {
            throw new OrganizationValidationException("Organization id is required.");
        }
        code = OrganizationValuePolicy.code(code, "Organization code");
        legalName = OrganizationValuePolicy.organizationName(legalName, "Organization legal name");
        brandName = OrganizationValuePolicy.displayName(brandName, "Organization brand name");
        supportEmail = OrganizationValuePolicy.email(supportEmail, "Organization support email");
        supportPhone = OrganizationValuePolicy.phone(supportPhone, "Organization support phone");
        defaultTimezone = OrganizationValuePolicy.timezone(defaultTimezone, false);
        defaultCurrency = OrganizationValuePolicy.currency(defaultCurrency);
        if (status == null) {
            throw new OrganizationValidationException("Organization status is required.");
        }
        if (createdAt == null || updatedAt == null) {
            throw new OrganizationValidationException("Organization timestamps are required.");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new OrganizationValidationException(
                    "Organization update timestamp must not precede creation.");
        }
        OrganizationValuePolicy.version(version, "Organization version");
    }
}
