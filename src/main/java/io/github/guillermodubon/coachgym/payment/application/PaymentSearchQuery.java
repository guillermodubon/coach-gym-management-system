package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.payment.domain.PaymentValidationException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record PaymentSearchQuery(
        UUID clientId,
        UUID membershipId,
        UUID membershipPeriodId,
        PaymentStatus status,
        PaymentMethod paymentMethod,
        Instant paidFrom,
        Instant paidUntil,
        int page,
        int size,
        PaymentSortField sortField,
        PaymentSortDirection direction) {

    public static final int MAX_SIZE = 100;

    public PaymentSearchQuery {

        if (page < 0) {
            throw new PaymentValidationException(
                    "Page must not be negative.");
        }

        if (size < 1 || size > MAX_SIZE) {
            throw new PaymentValidationException(
                    "Page size must be between 1 and 100.");
        }

        if (sortField == null) {
            throw new PaymentValidationException(
                    "Sort field must be provided.");
        }

        if (direction == null) {
            throw new PaymentValidationException(
                    "Sort direction must be provided.");
        }

        if (paidFrom != null && paidUntil != null
                && paidFrom.isAfter(paidUntil)) {
            throw new PaymentValidationException(
                    "paidFrom must not be after paidUntil.");
        }
    }

    public static PaymentSearchQuery from(
            UUID clientId,
            UUID membershipId,
            UUID membershipPeriodId,
            String status,
            String paymentMethod,
            Instant paidFrom,
            Instant paidUntil,
            int page,
            int size,
            String sort,
            String direction) {

        return new PaymentSearchQuery(
                clientId,
                membershipId,
                membershipPeriodId,
                parseStatus(status),
                parseMethod(paymentMethod),
                paidFrom,
                paidUntil,
                page,
                size,
                parseSortField(sort),
                parseSortDirection(direction));
    }

    private static PaymentStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PaymentStatus.valueOf(
                    value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new PaymentValidationException(
                    "Unsupported payment status filter.");
        }
    }

    private static PaymentMethod parseMethod(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PaymentMethod.valueOf(
                    value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new PaymentValidationException(
                    "Unsupported payment method filter.");
        }
    }

    private static PaymentSortField parseSortField(String value) {
        String normalized = (value == null || value.isBlank())
                ? "PAID_AT"
                : value.trim().toUpperCase(Locale.ROOT);
        try {
            return PaymentSortField.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new PaymentValidationException(
                    "Unsupported payment sort field.");
        }
    }

    private static PaymentSortDirection parseSortDirection(String value) {
        String normalized = (value == null || value.isBlank())
                ? "DESC"
                : value.trim().toUpperCase(Locale.ROOT);
        try {
            return PaymentSortDirection.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new PaymentValidationException(
                    "Unsupported payment sort direction.");
        }
    }
}
