package io.github.guillermodubon.coachgym.notification.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/** Empty request; recipient, subject, and attachment are server controlled. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
        description = "Empty request. Recipient, content, and attachment are server controlled.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record RequestEmailRequest() {

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported email request field: " + name);
    }
}
