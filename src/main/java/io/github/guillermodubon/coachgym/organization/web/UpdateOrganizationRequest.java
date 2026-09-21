package io.github.guillermodubon.coachgym.organization.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.guillermodubon.coachgym.organization.UpdateOrganizationCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Strict ADMIN request for editable canonical organization fields. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
        description = "Editable organization identity, contact, locale and optimistic-lock version. "
                + "Code, status, actor and timestamps are server controlled.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record UpdateOrganizationRequest(
        @NotBlank @Size(max = 255) String legalName,
        @NotBlank @Size(max = 160) String brandName,
        @Size(max = 254) String supportEmail,
        @Size(max = 32) String supportPhone,
        @NotBlank @Size(max = 64) String defaultTimezone,
        @NotBlank @Size(min = 3, max = 3) String defaultCurrency,
        @NotNull @PositiveOrZero Long version) {

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported organization request field: " + name);
    }

    UpdateOrganizationCommand toCommand() {
        return new UpdateOrganizationCommand(
                legalName,
                brandName,
                supportEmail,
                supportPhone,
                defaultTimezone,
                defaultCurrency,
                version);
    }
}
