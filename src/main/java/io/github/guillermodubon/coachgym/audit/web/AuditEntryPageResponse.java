package io.github.guillermodubon.coachgym.audit.web;

import io.github.guillermodubon.coachgym.audit.AuditEntryPage;
import java.util.List;
import java.util.Objects;

/** Bounded HTTP page of audit-entry summaries. */
record AuditEntryPageResponse(
        List<AuditEntrySummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    AuditEntryPageResponse {
        items = List.copyOf(items == null ? List.of() : items);
    }

    static AuditEntryPageResponse from(AuditEntryPage source) {
        Objects.requireNonNull(source, "Audit page must be provided.");
        return new AuditEntryPageResponse(
                source.items().stream()
                        .map(AuditEntrySummaryResponse::from)
                        .toList(),
                source.page(),
                source.size(),
                source.totalElements(),
                source.totalPages());
    }
}
