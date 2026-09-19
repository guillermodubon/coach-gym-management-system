package io.github.guillermodubon.coachgym.audit;

import java.util.List;

/** Immutable, bounded page of audit summaries. */
public record AuditEntryPage(
        List<AuditEntrySummary> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public AuditEntryPage {
        items = items == null ? List.of() : List.copyOf(items);
        if (page < 0) {
            throw new AuditQueryValidationException(
                    "Audit page must not be negative.");
        }
        if (size < 1 || size > AuditQueryPolicy.MAX_SIZE) {
            throw new AuditQueryValidationException(
                    "Audit page size must be between 1 and 100.");
        }
        if (totalElements < 0 || totalPages < 0) {
            throw new AuditQueryValidationException(
                    "Audit page totals must not be negative.");
        }
        if (items.size() > size) {
            throw new AuditQueryValidationException(
                    "Audit page items must not exceed the requested size.");
        }
        long expectedPages = totalElements == 0
                ? 0
                : ((totalElements - 1) / size) + 1;
        if (expectedPages > Integer.MAX_VALUE || totalPages != expectedPages) {
            throw new AuditQueryValidationException(
                    "Audit page total-pages value is inconsistent with totals.");
        }
    }

    public static AuditEntryPage of(
            List<AuditEntrySummary> items,
            int page,
            int size,
            long totalElements) {
        int totalPages = totalElements == 0
                ? 0
                : Math.toIntExact(((totalElements - 1) / size) + 1);
        return new AuditEntryPage(items, page, size, totalElements, totalPages);
    }
}
