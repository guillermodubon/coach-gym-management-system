package io.github.guillermodubon.coachgym.accesscredential.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.github.guillermodubon.coachgym.accesscredential.application.ReplaceAccessCredentialCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Operational reason and optimistic-lock version for atomic replacement. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
        description = "Reason and current version required to replace the active credential.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record ReplaceAccessCredentialRequest(
        @NotBlank @Size(min = 3, max = 2000) String reason,
        @PositiveOrZero long version) {

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported access credential request field: " + name);
    }

    ReplaceAccessCredentialCommand toCommand(UUID credentialId) {
        return new ReplaceAccessCredentialCommand(credentialId, reason, version);
    }
}
