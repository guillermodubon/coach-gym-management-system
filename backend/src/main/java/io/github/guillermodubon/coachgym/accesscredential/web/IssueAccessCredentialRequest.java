package io.github.guillermodubon.coachgym.accesscredential.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/** Empty request; credential identity and token material are server controlled. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
        description = "Empty request. Credential identity, token and lifecycle fields are server controlled.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record IssueAccessCredentialRequest() {

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported access credential request field: " + name);
    }
}
