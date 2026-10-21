package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentAttemptDetails;
import java.util.List;
import java.util.Objects;

/** Immutable bounded page of safe payment-attempt projections. */
public record PaymentAttemptPage(
        List<PaymentAttemptDetails> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public PaymentAttemptPage {
        items = List.copyOf(Objects.requireNonNull(items, "Payment-attempt items are required."));
        if (page < 0 || size < 1 || totalElements < 0 || totalPages < 0
                || items.size() > size) {
            throw new IllegalArgumentException("Payment-attempt page metadata is invalid.");
        }
        long expectedPages = totalElements / size + (totalElements % size == 0 ? 0 : 1);
        int boundedExpectedPages = expectedPages > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) expectedPages;
        if (totalPages != boundedExpectedPages) {
            throw new IllegalArgumentException("Payment-attempt page count is inconsistent.");
        }
    }
}
