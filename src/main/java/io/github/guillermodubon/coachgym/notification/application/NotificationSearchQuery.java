package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.NotificationResourceType;
import io.github.guillermodubon.coachgym.notification.NotificationSeverity;
import io.github.guillermodubon.coachgym.notification.NotificationType;
import io.github.guillermodubon.coachgym.notification.domain.NotificationValidationException;
import java.time.Duration;
import java.time.Instant;

/** Validated filters and pagination for the authenticated user's inbox. */
public record NotificationSearchQuery(
        NotificationReadFilter readFilter,
        NotificationType notificationType,
        NotificationSeverity severity,
        NotificationResourceType resourceType,
        Instant createdFrom,
        Instant createdUntil,
        int page,
        int size,
        NotificationSortField sortField,
        NotificationSortDirection sortDirection) {

    public static final Duration MAX_DATE_RANGE = Duration.ofDays(366);

    public NotificationSearchQuery {
        if (readFilter == null) {
            readFilter = NotificationReadFilter.ALL;
        }
        if (page < 0) {
            throw new NotificationValidationException(
                    "Notification page index must not be negative.");
        }
        if (size < 1 || size > 100) {
            throw new NotificationValidationException(
                    "Notification page size must be between 1 and 100.");
        }
        if (createdFrom != null && createdUntil != null
                && createdFrom.isAfter(createdUntil)) {
            throw new NotificationValidationException(
                    "Notification created-from timestamp must not be after created-until timestamp.");
        }
        if (createdFrom != null && createdUntil != null
                && MAX_DATE_RANGE.compareTo(Duration.between(createdFrom, createdUntil)) < 0) {
            throw new NotificationValidationException(
                    "Notification created date range must not exceed 366 days.");
        }
        if (sortField == null) {
            sortField = NotificationSortField.CREATED_AT;
        }
        if (sortDirection == null) {
            sortDirection = NotificationSortDirection.DESC;
        }
    }

    public static NotificationSearchQuery defaults() {
        return new NotificationSearchQuery(
                NotificationReadFilter.ALL, null, null, null, null, null,
                0, 25, NotificationSortField.CREATED_AT,
                NotificationSortDirection.DESC);
    }
}
