package io.github.guillermodubon.coachgym.organization.application;

import io.github.guillermodubon.coachgym.organization.GymBranchStatus;

/** Validated, bounded query for the canonical branch catalog. */
public record GymBranchSearchQuery(
        GymBranchStatus status,
        String search,
        String city,
        String countryCode,
        int page,
        int size,
        GymBranchSortField sortField,
        GymBranchSortDirection direction) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;
    private static final int MAX_FILTER_LENGTH = 120;

    /** Backward-compatible constructor for application callers without city filters. */
    public GymBranchSearchQuery(
            GymBranchStatus status,
            String search,
            int page,
            int size,
            GymBranchSortField sortField,
            GymBranchSortDirection direction) {
        this(status, search, null, null, page, size, sortField, direction);
    }

    public GymBranchSearchQuery {
        if (page < 0) {
            throw new IllegalArgumentException("Branch search page must not be negative.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("Branch search size must be between 1 and 100.");
        }
        search = normalize(search, "Branch search");
        city = normalize(city, "Branch city filter");
        countryCode = normalize(countryCode, "Branch country filter");
        if (countryCode != null) {
            countryCode = countryCode.toUpperCase(java.util.Locale.ROOT);
            if (!countryCode.matches("[A-Z]{2}")) {
                throw new IllegalArgumentException(
                        "Branch country filter must be a two-letter ISO 3166 code.");
            }
        }
        sortField = sortField == null ? GymBranchSortField.CODE : sortField;
        direction = direction == null ? GymBranchSortDirection.ASC : direction;
    }

    public static GymBranchSearchQuery defaults() {
        return new GymBranchSearchQuery(
                null,
                null,
                DEFAULT_PAGE,
                DEFAULT_SIZE,
                GymBranchSortField.CODE,
                GymBranchSortDirection.ASC);
    }

    private static String normalize(String value, String field) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_FILTER_LENGTH) {
            throw new IllegalArgumentException(field + " filter is too long.");
        }
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " filter contains a control character.");
        }
        return normalized;
    }
}
