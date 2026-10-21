package io.github.guillermodubon.coachgym.membership.application;

import java.util.List;
import java.util.Objects;

/** Immutable page of memberships within one resolved branch. */
public record MembershipPage(
        List<MembershipReadEntry> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public MembershipPage {
        items = List.copyOf(Objects.requireNonNull(items, "Membership page items are required."));
        if (page < 0 || size < 1 || totalElements < 0 || totalPages < 0) {
            throw new IllegalArgumentException("Membership page metadata is invalid.");
        }
    }
}
