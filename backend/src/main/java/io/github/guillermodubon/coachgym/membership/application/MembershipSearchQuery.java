package io.github.guillermodubon.coachgym.membership.application;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import java.util.UUID;

/** Validated, bounded query for memberships visible in one authorized branch. */
public record MembershipSearchQuery(
        String search,
        UUID clientId,
        MembershipStatus status,
        UUID branchId,
        int page,
        int size,
        MembershipSortField sort,
        MembershipSortDirection direction) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;
    private static final int MAX_SEARCH_LENGTH = 120;

    public MembershipSearchQuery {
        if (page < 0) {
            throw new MembershipQueryValidationException(
                    "Membership page must not be negative.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new MembershipQueryValidationException(
                    "Membership page size must be between 1 and 100.");
        }
        search = normalizeSearch(search);
        sort = sort == null ? MembershipSortField.CREATED_AT : sort;
        direction = direction == null ? MembershipSortDirection.DESC : direction;
    }

    public long offset() {
        return Math.multiplyExact((long) page, size);
    }

    private static String normalizeSearch(String value) {
        if (value == null) {
            return null;
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new MembershipQueryValidationException(
                    "Membership code search contains a control character.");
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_SEARCH_LENGTH) {
            throw new MembershipQueryValidationException(
                    "Membership code search is too long.");
        }
        return normalized;
    }
}
