package io.github.guillermodubon.coachgym.organization.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.guillermodubon.coachgym.organization.OrganizationDetails;
import io.github.guillermodubon.coachgym.organization.OrganizationStatus;
import io.github.guillermodubon.coachgym.organization.OrganizationSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** HTTP projection for the canonical organization. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Canonical organization. Contact and legal fields are returned only to ADMIN sessions.")
public record OrganizationResponse(
        UUID id,
        String code,
        String legalName,
        String brandName,
        String supportEmail,
        String supportPhone,
        String defaultTimezone,
        String defaultCurrency,
        OrganizationStatus status,
        Long version,
        Instant updatedAt) {

    static OrganizationResponse from(OrganizationDetails details) {
        return new OrganizationResponse(
                details.id(),
                details.code(),
                details.legalName(),
                details.brandName(),
                details.supportEmail(),
                details.supportPhone(),
                details.defaultTimezone(),
                details.defaultCurrency(),
                details.status(),
                details.version(),
                details.updatedAt());
    }

    static OrganizationResponse from(OrganizationSummary summary) {
        return new OrganizationResponse(
                summary.id(),
                summary.code(),
                null,
                summary.brandName(),
                null,
                null,
                summary.defaultTimezone(),
                summary.defaultCurrency(),
                summary.status(),
                null,
                null);
    }
}
