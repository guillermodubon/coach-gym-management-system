package io.github.guillermodubon.coachgym.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CsrfTokenResponse", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record CsrfTokenResponse(
        @Schema(description = "Token value returned to the caller for the configured CSRF header.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String token,
        @Schema(description = "HTTP header name expected by the CSRF filter.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String headerName,
        @Schema(description = "Form parameter name supported by the CSRF filter; this is not a query parameter.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String parameterName) {
}
