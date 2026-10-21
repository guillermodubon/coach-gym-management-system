package io.github.guillermodubon.coachgym.shared.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.time.Instant;

/** OpenAPI projection of the flattened RFC 9457 response emitted by ApiProblemFactory. */
@Schema(
        name = "ApiProblemResponse",
        description = "RFC 9457 error response with stable Coach Gym code and timestamp extensions.",
        additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
public record ApiProblemResponse(
        @Schema(description = "Problem type identifier.", requiredMode = Schema.RequiredMode.REQUIRED)
        URI type,
        @Schema(description = "HTTP reason phrase.", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(description = "HTTP status code.", requiredMode = Schema.RequiredMode.REQUIRED)
        int status,
        @Schema(description = "Safe problem description.", requiredMode = Schema.RequiredMode.REQUIRED)
        String detail,
        @Schema(description = "Request URI when available.")
        URI instance,
        @Schema(description = "Stable application error code.", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Time the problem was created.", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant timestamp,
        @Schema(description = "Request correlation identifier when available.")
        String correlationId) {}
