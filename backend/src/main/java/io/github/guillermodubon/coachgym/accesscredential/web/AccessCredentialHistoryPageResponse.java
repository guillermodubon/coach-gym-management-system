package io.github.guillermodubon.coachgym.accesscredential.web;

import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialHistoryPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Paginated, newest-first credential lifecycle history. */
@Schema(description = "Paginated access-credential lifecycle history.")
record AccessCredentialHistoryPageResponse(
        List<AccessCredentialHistoryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty) {

    static AccessCredentialHistoryPageResponse from(AccessCredentialHistoryPage result) {
        List<AccessCredentialHistoryResponse> content = result.items().stream()
                .map(AccessCredentialHistoryResponse::from)
                .toList();
        return new AccessCredentialHistoryPageResponse(
                content,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.page() == 0,
                result.page() + 1 >= result.totalPages(),
                content.isEmpty());
    }
}
