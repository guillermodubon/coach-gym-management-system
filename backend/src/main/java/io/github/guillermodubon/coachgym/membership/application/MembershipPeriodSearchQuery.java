package io.github.guillermodubon.coachgym.membership.application;

/** Validated page request for immutable periods belonging to one membership. */
public record MembershipPeriodSearchQuery(
        int page,
        int size,
        MembershipSortDirection direction) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;

    public MembershipPeriodSearchQuery {
        if (page < 0) {
            throw new MembershipQueryValidationException(
                    "Membership period page must not be negative.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new MembershipQueryValidationException(
                    "Membership period page size must be between 1 and 100.");
        }
        direction = direction == null ? MembershipSortDirection.DESC : direction;
    }

    public long offset() {
        return (long) page * size;
    }
}
