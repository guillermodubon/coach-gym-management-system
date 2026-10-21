package io.github.guillermodubon.coachgym.shared.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** Marker for the explicitly registered OpenAPI 3.1 null schema. */
@Schema(hidden = true)
public final class OpenApiNullValue {

    private OpenApiNullValue() {}
}
