package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.application.StaffDirectoryPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record StaffDirectoryPageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<StaffDirectoryEntryResponse> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages) {

    static StaffDirectoryPageResponse from(StaffDirectoryPage page) {
        return new StaffDirectoryPageResponse(
                page.items().stream().map(StaffDirectoryEntryResponse::from).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
