package io.github.guillermodubon.coachgym.client;

import java.util.Locale;
import java.util.UUID;

/** Validated and normalized query for the operational client catalog. */
public record ClientSearchQuery(
        String search,
        ClientStatus status,
        String membershipStatus,
        int page,
        int size,
        ClientSortField sort,
        ClientSortDirection direction,
        UUID branchId) {

    public ClientSearchQuery(
            String search,
            ClientStatus status,
            String membershipStatus,
            int page,
            int size,
            ClientSortField sort,
            ClientSortDirection direction) {
        this(search, status, membershipStatus, page, size, sort, direction, null);
    }

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;

    public ClientSearchQuery {
        search = normalize(search);
        membershipStatus = normalizeEnumFilter(membershipStatus);

        if (page < 0) {
            throw new IllegalArgumentException(
                    "Client search page must not be negative.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Client search size must be between 1 and 100.");
        }
        sort = sort == null ? ClientSortField.LAST_NAME : sort;
        direction = direction == null ? ClientSortDirection.ASC : direction;
    }

    public static ClientSearchQuery defaults() {
        return new ClientSearchQuery(
                null,
                null,
                null,
                DEFAULT_PAGE,
                DEFAULT_SIZE,
                ClientSortField.LAST_NAME,
                ClientSortDirection.ASC,
                null);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeEnumFilter(String value) {
        String normalized = normalize(value);
        return normalized == null
                ? null
                : normalized.toUpperCase(Locale.ROOT);
    }
}
