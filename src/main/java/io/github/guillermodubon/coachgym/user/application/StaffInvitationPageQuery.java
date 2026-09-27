package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.StaffInvitationStatus;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Instant;
import java.util.UUID;

/** Organization-scoped persistence query with prevalidated values and allowlisted sorting. */
public record StaffInvitationPageQuery(
        UUID organizationId,
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

    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_OFFSET = 10_000;

    public StaffInvitationPageQuery {
        if (organizationId == null || page < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE
                || (long) page * pageSize > MAX_OFFSET) {
            throw new IllegalArgumentException("Invitation page query is outside allowed bounds.");
        }
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)
                || expiresFrom != null && expiresTo != null && expiresFrom.isAfter(expiresTo)) {
            throw new IllegalArgumentException("Invitation date range is invalid.");
        }
        sort = sort == null ? StaffInvitationSortField.CREATED_AT : sort;
        direction = direction == null ? StaffInvitationSortDirection.DESC : direction;
    }

    public StaffInvitationPageQuery(
            UUID organizationId,
            StaffInvitationStatus status,
            int page,
            int pageSize) {
        this(organizationId, status, null, null, null, null,
                null, null, null, null, page, pageSize,
                StaffInvitationSortField.CREATED_AT,
                StaffInvitationSortDirection.DESC);
    }
}
