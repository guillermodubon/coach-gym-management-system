package io.github.guillermodubon.coachgym.payment;

import java.util.List;

/** Immutable page of append-only payment status transitions. */
public record PaymentStatusHistoryPage(
        List<PaymentStatusHistoryDetails> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public PaymentStatusHistoryPage {
        if (content == null) {
            throw new IllegalArgumentException("Payment history content is required.");
        }
        content = List.copyOf(content);
        if (page < 0) {
            throw new IllegalArgumentException("Payment history page must not be negative.");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Payment history page size must be between 1 and 100.");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException(
                    "Payment history total elements must not be negative.");
        }
        if (totalPages < 0) {
            throw new IllegalArgumentException(
                    "Payment history total pages must not be negative.");
        }
        int expectedPages = totalElements == 0
                ? 0
                : (int) ((totalElements + size - 1) / size);
        if (totalPages != expectedPages) {
            throw new IllegalArgumentException(
                    "Payment history total pages is inconsistent.");
        }
        if (content.size() > size) {
            throw new IllegalArgumentException(
                    "Payment history content exceeds page size.");
        }
        if (totalElements == 0 && !content.isEmpty()) {
            throw new IllegalArgumentException(
                    "Empty payment history cannot contain entries.");
        }
    }

    public boolean empty() {
        return content.isEmpty();
    }

    public boolean first() {
        return page == 0;
    }

    public boolean last() {
        return totalPages == 0 || page >= totalPages - 1;
    }
}
