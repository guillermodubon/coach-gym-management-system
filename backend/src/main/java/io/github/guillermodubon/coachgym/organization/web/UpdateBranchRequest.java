package io.github.guillermodubon.coachgym.organization.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.guillermodubon.coachgym.organization.UpdateGymBranchCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Strict ADMIN request for editable branch detail fields. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
        description = "Editable branch detail and optimistic-lock version. Code, organization, "
                + "status, initial marker, actor and timestamps are server controlled.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record UpdateBranchRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @Size(max = 120) String city,
        @Size(max = 120) String stateOrDepartment,
        @Size(max = 32) String postalCode,
        @Size(min = 2, max = 2) String countryCode,
        @Size(max = 32) String phone,
        @Size(max = 254) String email,
        @Size(max = 64) String timezone,
        @NotNull @PositiveOrZero Long version) {

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported branch update field: " + name);
    }

    UpdateGymBranchCommand toCommand() {
        return new UpdateGymBranchCommand(
                name,
                addressLine1,
                addressLine2,
                city,
                stateOrDepartment,
                postalCode,
                countryCode,
                phone,
                email,
                timezone,
                version);
    }
}
