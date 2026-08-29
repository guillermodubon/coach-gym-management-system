package io.github.guillermodubon.coachgym.access.application;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.github.guillermodubon.coachgym.access.domain.AccessValidationException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Validated query for listing access records.
 *
 * <p>Construct via {@link #from(UUID, UUID, String, String, Instant, Instant, UUID, int, int, String, String)}
 * which parses and validates all string parameters. Compact-constructor
 * validation guards page/size/sort integrity.</p>
 *
 * <p>Approved filters: {@code clientId}, {@code membershipId}, {@code result},
 * {@code reasonCode}, {@code checkedInFrom}, {@code checkedInUntil},
 * {@code processedByUserId}.</p>
 *
 * <p>Only sort field: {@link AccessSortField#CHECKED_IN_AT}.
 * Default: {@code CHECKED_IN_AT DESC}.</p>
 */
public record AccessRecordSearchQuery(
        UUID clientId,
        UUID membershipId,
        AccessResult result,
        AccessReasonCode reasonCode,
        Instant checkedInFrom,
        Instant checkedInUntil,
        UUID processedByUserId,
        int page,
        int size,
        AccessSortField sortField,
        AccessSortDirection direction) {

    public static final int MAX_SIZE = 100;

    public AccessRecordSearchQuery {

        if (page < 0) {
            throw new AccessValidationException(
                    "Page must not be negative.");
        }

        if (size < 1 || size > MAX_SIZE) {
            throw new AccessValidationException(
                    "Page size must be between 1 and 100.");
        }

        if (sortField == null) {
            throw new AccessValidationException(
                    "Sort field must be provided.");
        }

        if (direction == null) {
            throw new AccessValidationException(
                    "Sort direction must be provided.");
        }

        if (checkedInFrom != null && checkedInUntil != null
                && checkedInFrom.isAfter(checkedInUntil)) {
            throw new AccessValidationException(
                    "checkedInFrom must not be after checkedInUntil.");
        }
    }

    /**
     * Parses and validates all string parameters, applying defaults for
     * sort field and direction when absent.
     */
    public static AccessRecordSearchQuery from(
            UUID clientId,
            UUID membershipId,
            String result,
            String reasonCode,
            Instant checkedInFrom,
            Instant checkedInUntil,
            UUID processedByUserId,
            int page,
            int size,
            String sort,
            String direction) {

        return new AccessRecordSearchQuery(
                clientId,
                membershipId,
                parseResult(result),
                parseReasonCode(reasonCode),
                checkedInFrom,
                checkedInUntil,
                processedByUserId,
                page,
                size,
                parseSortField(sort),
                parseSortDirection(direction));
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    private static AccessResult parseResult(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AccessResult.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AccessValidationException(
                    "Unsupported access result filter: " + value.trim() + ".");
        }
    }

    private static AccessReasonCode parseReasonCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AccessReasonCode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AccessValidationException(
                    "Unsupported access reason code filter: " + value.trim() + ".");
        }
    }

    private static AccessSortField parseSortField(String value) {
        String normalised = (value == null || value.isBlank())
                ? "CHECKED_IN_AT"
                : value.trim().toUpperCase(Locale.ROOT);
        try {
            return AccessSortField.valueOf(normalised);
        } catch (IllegalArgumentException e) {
            throw new AccessValidationException(
                    "Unsupported access sort field: " + value.trim() + ".");
        }
    }

    private static AccessSortDirection parseSortDirection(String value) {
        String normalised = (value == null || value.isBlank())
                ? "DESC"
                : value.trim().toUpperCase(Locale.ROOT);
        try {
            return AccessSortDirection.valueOf(normalised);
        } catch (IllegalArgumentException e) {
            throw new AccessValidationException(
                    "Unsupported access sort direction: " + value.trim() + ".");
        }
    }
}
