package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.NotificationDetails;
import io.github.guillermodubon.coachgym.notification.domain.NotificationValidationException;
import java.util.List;

/** Immutable paginated notification result. */
public record NotificationPage(
        List<NotificationDetails> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public NotificationPage {
        if (items == null) {
            throw new NotificationValidationException(
                    "Notification page items are required.");
        }
        items = List.copyOf(items);
        if (page < 0) {
            throw new NotificationValidationException(
                    "Notification page index must not be negative.");
        }
        if (size < 1) {
            throw new NotificationValidationException(
                    "Notification page size must be positive.");
        }
        if (totalElements < 0 || totalPages < 0) {
            throw new NotificationValidationException(
                    "Notification page totals must not be negative.");
        }
        if (items.size() > size) {
            throw new NotificationValidationException(
                    "Notification page items must not exceed page size.");
        }
        int expectedPages = totalElements == 0
                ? 0
                : (int) Math.ceil((double) totalElements / size);
        if (totalPages != expectedPages) {
            throw new NotificationValidationException(
                    "Notification total pages are inconsistent with total elements and page size.");
        }
    }
}
