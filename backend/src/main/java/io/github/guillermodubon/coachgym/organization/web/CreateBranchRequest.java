package io.github.guillermodubon.coachgym.organization.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.guillermodubon.coachgym.organization.CreateGymBranchCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Strict ADMIN request for creating a branch under the canonical organization. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
        description = "Branch identity, address and contact fields. Organization, status, "
                + "initial marker, actor and timestamps are server controlled.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record CreateBranchRequest(
        @NotBlank @Size(max = 32) String code,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @Size(max = 120) String city,
        @Size(max = 120) String stateOrDepartment,
        @Size(max = 32) String postalCode,
        @Size(min = 2, max = 2) String countryCode,
        @Size(max = 32) String phone,
        @Size(max = 254) String email,
        @Size(max = 64) String timezone) {

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported branch creation field: " + name);
    }

    CreateGymBranchCommand toCommand() {
        return new CreateGymBranchCommand(
                code,
                name,
                addressLine1,
                addressLine2,
                city,
                stateOrDepartment,
                postalCode,
                countryCode,
                phone,
                email,
                timezone);
    }
}
