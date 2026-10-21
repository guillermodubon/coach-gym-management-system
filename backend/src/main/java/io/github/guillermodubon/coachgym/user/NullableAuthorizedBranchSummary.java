package io.github.guillermodubon.coachgym.user;

import io.github.guillermodubon.coachgym.shared.web.OpenApiNullValue;
import io.swagger.v3.oas.annotations.media.Schema;

/** OpenAPI schema for a present active-branch field that may contain null. */
@Schema(
        name = "NullableAuthorizedBranchSummary",
        anyOf = {AuthorizedBranchSummary.class, OpenApiNullValue.class})
public final class NullableAuthorizedBranchSummary {

    private NullableAuthorizedBranchSummary() {}
}
