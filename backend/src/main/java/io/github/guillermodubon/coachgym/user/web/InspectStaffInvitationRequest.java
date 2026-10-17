package io.github.guillermodubon.coachgym.user.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Token is accepted in the body only and never appears in diagnostics or URLs. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record InspectStaffInvitationRequest(@NotBlank @Size(max = 512) String token) {

    @Override
    public String toString() {
        return "InspectStaffInvitationRequest[tokenPresent=" + (token != null) + ']';
    }

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported invitation inspection field: " + name);
    }
}
