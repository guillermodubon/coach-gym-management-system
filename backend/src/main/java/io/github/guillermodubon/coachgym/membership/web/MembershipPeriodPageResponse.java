package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.membership.application.MembershipPeriodPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Explicit paginated HTTP representation for a membership's period history. */
@Schema(name = "MembershipPeriodPageResponse", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record MembershipPeriodPageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<MembershipPeriodResponse> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages) {

    public MembershipPeriodPageResponse {
        items = List.copyOf(items);
    }

    static MembershipPeriodPageResponse from(MembershipPeriodPage page) {
        return new MembershipPeriodPageResponse(
                page.items().stream()
                        .map(entry -> MembershipPeriodResponse.from(
                                entry.period(), entry.coverage()))
                        .toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}
