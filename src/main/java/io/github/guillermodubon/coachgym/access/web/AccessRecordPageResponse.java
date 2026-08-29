package io.github.guillermodubon.coachgym.access.web;

import io.github.guillermodubon.coachgym.access.application.AccessRecordPage;
import java.util.List;

/**
 * Paginated response containing access attempt history.
 */
public record AccessRecordPageResponse(
        List<AccessRecordResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public AccessRecordPageResponse {
        items = List.copyOf(items);
    }

    public static AccessRecordPageResponse from(
            AccessRecordPage source) {

        if (source == null) {
            throw new IllegalArgumentException(
                    "Access record page must be provided.");
        }

        return new AccessRecordPageResponse(
                source.items().stream()
                        .map(AccessRecordResponse::from)
                        .toList(),
                source.page(),
                source.size(),
                source.totalElements(),
                source.totalPages());
    }
}

