package io.github.guillermodubon.coachgym.access.application;

import io.github.guillermodubon.coachgym.access.AccessRecordDetails;
import java.util.List;

/** Paginated result for access-record listing. */
public record AccessRecordPage(
        List<AccessRecordDetails> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public AccessRecordPage {
        items = List.copyOf(items);
    }
}
