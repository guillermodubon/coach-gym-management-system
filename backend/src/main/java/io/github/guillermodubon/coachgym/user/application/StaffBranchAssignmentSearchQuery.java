package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Bounded, parameterized query for administrative assignment history. */
public record StaffBranchAssignmentSearchQuery(
        UUID userId,
        String search,
        RoleCode role,
        StaffScopeType scopeType,
        UUID branchId,
        StaffBranchAssignmentStatus status,
        Instant assignedFrom,
        Instant assignedTo,
        int page,
        int size,
        StaffBranchAssignmentSortField sortField,
        StaffBranchAssignmentSortDirection direction) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;
    private static final int MAX_SEARCH_LENGTH = 120;
    private static final Duration MAX_DATE_RANGE = Duration.ofDays(366);

    public StaffBranchAssignmentSearchQuery {
        if (page < 0) {
            throw new IllegalArgumentException("Assignment search page must not be negative.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("Assignment search size must be between 1 and 100.");
        }
        search = normalizeSearch(search);
        if (assignedFrom != null && assignedTo != null) {
            if (assignedTo.isBefore(assignedFrom)
                    || Duration.between(assignedFrom, assignedTo).compareTo(MAX_DATE_RANGE) > 0) {
                throw new IllegalArgumentException("Assignment date range is invalid or too broad.");
            }
        }
        sortField = sortField == null
                ? StaffBranchAssignmentSortField.ASSIGNED_AT : sortField;
        direction = direction == null
                ? StaffBranchAssignmentSortDirection.DESC : direction;
    }

    public static StaffBranchAssignmentSearchQuery defaults() {
        return new StaffBranchAssignmentSearchQuery(
                null, null, null, null, null, null, null, null,
                DEFAULT_PAGE, DEFAULT_SIZE,
                StaffBranchAssignmentSortField.ASSIGNED_AT,
                StaffBranchAssignmentSortDirection.DESC);
    }

    public StaffBranchAssignmentSearchQuery forUser(UUID targetUserId) {
        return new StaffBranchAssignmentSearchQuery(
                targetUserId, search, role, scopeType, branchId, status,
                assignedFrom, assignedTo, page, size, sortField, direction);
    }

    private static String normalizeSearch(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_SEARCH_LENGTH
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Assignment search filter is too long or unsafe.");
        }
        return normalized;
    }
}
