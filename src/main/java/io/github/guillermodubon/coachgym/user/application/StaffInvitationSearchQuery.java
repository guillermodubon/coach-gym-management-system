package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffInvitationStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Validated, bounded filter contract for the organization-admin invitation list. */
public record StaffInvitationSearchQuery(
        StaffInvitationStatus status,
        RoleCode role,
        StaffScopeType scope,
        UUID branchId,
        String emailQuery,
        Instant createdFrom,
        Instant createdTo,
        Instant expiresFrom,
        Instant expiresTo,
        int page,
        int pageSize,
        StaffInvitationSortField sort,
        StaffInvitationSortDirection direction) {

    public StaffInvitationSearchQuery {
        if (page < 0 || pageSize < 1 || pageSize > StaffInvitationPageQuery.MAX_PAGE_SIZE
                || (long) page * pageSize > StaffInvitationPageQuery.MAX_OFFSET) {
            throw new IllegalArgumentException("Invitation page query is outside allowed bounds.");
        }
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new IllegalArgumentException("Invitation creation range is invalid.");
        }
        if (expiresFrom != null && expiresTo != null && expiresFrom.isAfter(expiresTo)) {
            throw new IllegalArgumentException("Invitation expiration range is invalid.");
        }
        emailQuery = normalizeEmailQuery(emailQuery);
        sort = sort == null ? StaffInvitationSortField.CREATED_AT : sort;
        direction = direction == null ? StaffInvitationSortDirection.DESC : direction;
    }

    private static String normalizeEmailQuery(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() < 3 || normalized.length() > 64
                || normalized.chars().anyMatch(Character::isISOControl)
                || normalized.contains("%") || normalized.contains("_")
                || !normalized.matches("[a-z0-9.!#$&'*+/=?^`{|}~-]+@[a-z0-9.-]*|[a-z0-9.!#$&'*+/=?^`{|}~-]+")) {
            throw new IllegalArgumentException("Invitation email filter is invalid.");
        }
        return normalized;
    }

    @Override
    public String toString() {
        return "StaffInvitationSearchQuery[status=" + status
                + ", role=" + role
                + ", scope=" + scope
                + ", branchFilterPresent=" + (branchId != null)
                + ", emailFilterPresent=" + (emailQuery != null)
                + ", createdRangePresent=" + (createdFrom != null || createdTo != null)
                + ", expirationRangePresent=" + (expiresFrom != null || expiresTo != null)
                + ", page=" + page
                + ", pageSize=" + pageSize
                + ", sort=" + sort
                + ", direction=" + direction + ']';
    }
}
