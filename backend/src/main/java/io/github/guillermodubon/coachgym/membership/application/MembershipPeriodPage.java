package io.github.guillermodubon.coachgym.membership.application;

import java.util.List;
import java.util.Objects;

/** Immutable page of historical periods from one authorized membership. */
public record MembershipPeriodPage(
        List<MembershipPeriodReadEntry> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public MembershipPeriodPage {
        items = List.copyOf(Objects.requireNonNull(items, "Period page items are required."));
        if (page < 0 || size < 1 || totalElements < 0 || totalPages < 0) {
            throw new IllegalArgumentException("Membership period page metadata is invalid.");
        }
    }
}
