package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.membership.application.MembershipPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Explicit paginated HTTP representation for membership search. */
@Schema(name = "MembershipPageResponse", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record MembershipPageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<MembershipResponse> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages) {

    public MembershipPageResponse {
        items = List.copyOf(items);
    }

    static MembershipPageResponse from(MembershipPage page) {
        return new MembershipPageResponse(
                page.items().stream()
                        .map(entry -> MembershipResponse.from(
                                entry.membership(), entry.currentPeriodCoverage()))
                        .toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}
