package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.util.Locale;
import java.util.UUID;

/** Validated, bounded query for the administrative staff directory. */
public record StaffDirectorySearchQuery(
        String search,
        StaffAccountStatus status,
        RoleCode role,
        StaffScopeType scopeType,
        UUID branchId,
        int page,
        int size,
        StaffDirectorySortField sort,
        StaffDirectorySortDirection direction) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;
    public static final int MAX_SEARCH_LENGTH = 120;

    public StaffDirectorySearchQuery {
        if (page < 0) {
            throw new IllegalArgumentException("Directory page must not be negative.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("Directory size must be between 1 and 100.");
        }
        search = normalizeSearch(search);
        sort = sort == null ? StaffDirectorySortField.LAST_NAME : sort;
        direction = direction == null ? StaffDirectorySortDirection.ASC : direction;
    }

    public static StaffDirectorySearchQuery defaults() {
        return new StaffDirectorySearchQuery(
                null, null, null, null, null,
                DEFAULT_PAGE, DEFAULT_SIZE,
                StaffDirectorySortField.LAST_NAME,
                StaffDirectorySortDirection.ASC);
    }

    private static String normalizeSearch(String value) {
        if (value == null) {
            return null;
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Directory search filter is too long or unsafe.");
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_SEARCH_LENGTH) {
            throw new IllegalArgumentException("Directory search filter is too long or unsafe.");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
