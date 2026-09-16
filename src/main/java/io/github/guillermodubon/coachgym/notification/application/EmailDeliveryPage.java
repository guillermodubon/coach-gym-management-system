package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryDetails;
import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValidationException;
import java.util.List;

/** Immutable page of delivery metadata with stable pagination semantics. */
public record EmailDeliveryPage(
        List<EmailDeliveryDetails> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public EmailDeliveryPage {
        if (items == null) {
            throw new EmailDeliveryValidationException("Email delivery page items are required.");
        }
        items = List.copyOf(items);
        if (page < 0 || size < 1) {
            throw new EmailDeliveryValidationException("Email delivery page bounds are invalid.");
        }
        if (totalElements < 0 || totalPages < 0 || items.size() > size) {
            throw new EmailDeliveryValidationException("Email delivery page totals are invalid.");
        }
        int expectedPages = totalElements == 0
                ? 0 : (int) Math.ceil((double) totalElements / size);
        if (totalPages != expectedPages) {
            throw new EmailDeliveryValidationException(
                    "Email delivery page totals are inconsistent.");
        }
    }
}
