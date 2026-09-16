package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryStatus;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValidationException;
import java.time.Instant;
import java.util.UUID;

/** Validated filters for operational delivery history queries. */
public record EmailDeliverySearchQuery(
        EmailDeliveryType deliveryType,
        EmailDeliveryStatus status,
        UUID clientId,
        UUID sourceResourceId,
        Instant requestedFrom,
        Instant requestedUntil,
        int page,
        int size,
        EmailDeliverySortField sortField,
        EmailDeliverySortDirection sortDirection) {

    public static final int MAX_SIZE = 100;

    public EmailDeliverySearchQuery {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new EmailDeliveryValidationException(
                    "Email delivery page size must be between 1 and 100.");
        }
        if (requestedFrom != null && requestedUntil != null
                && requestedFrom.isAfter(requestedUntil)) {
            throw new EmailDeliveryValidationException(
                    "Email delivery requested-from must not be after requested-until.");
        }
        if (sortField == null) {
            sortField = EmailDeliverySortField.REQUESTED_AT;
        }
        if (sortDirection == null) {
            sortDirection = EmailDeliverySortDirection.DESC;
        }
    }

    public static EmailDeliverySearchQuery defaults() {
        return new EmailDeliverySearchQuery(
                null, null, null, null, null, null,
                0, 25, EmailDeliverySortField.REQUESTED_AT,
                EmailDeliverySortDirection.DESC);
    }
}
