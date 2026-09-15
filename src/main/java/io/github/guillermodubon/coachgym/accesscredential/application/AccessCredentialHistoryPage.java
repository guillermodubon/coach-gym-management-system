package io.github.guillermodubon.coachgym.accesscredential.application;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialHistoryDetails;
import java.util.List;

/** Immutable newest-first page of credential lifecycle history. */
public record AccessCredentialHistoryPage(
        List<AccessCredentialHistoryDetails> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public AccessCredentialHistoryPage {
        if (items == null) {
            throw new IllegalArgumentException("Credential history items are required.");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Credential history pagination is invalid.");
        }
        if (totalElements < 0 || totalPages < 0) {
            throw new IllegalArgumentException("Credential history totals are invalid.");
        }
        items = List.copyOf(items);
    }
}
