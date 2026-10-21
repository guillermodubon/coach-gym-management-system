package io.github.guillermodubon.coachgym.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipSearchQueryTest {

    @Test
    void normalizesSearchAndAppliesStableDefaults() {
        MembershipSearchQuery query = new MembershipSearchQuery(
                "  mem-  000123  ", null, null, null, 0, 25, null, null);

        assertThat(query.search()).isEqualTo("mem- 000123");
        assertThat(query.sort()).isEqualTo(MembershipSortField.CREATED_AT);
        assertThat(query.direction()).isEqualTo(MembershipSortDirection.DESC);
        assertThat(query.offset()).isZero();
    }

    @Test
    void preservesWildcardCharactersAsLiteralSearchInput() {
        MembershipSearchQuery query = new MembershipSearchQuery(
                "%_\\", UUID.randomUUID(), MembershipStatus.CANCELLED,
                UUID.randomUUID(), 3, 10,
                MembershipSortField.MEMBERSHIP_CODE, MembershipSortDirection.ASC);

        assertThat(query.search()).isEqualTo("%_\\");
        assertThat(query.status()).isEqualTo(MembershipStatus.CANCELLED);
        assertThat(query.offset()).isEqualTo(30);
    }

    @Test
    void rejectsInvalidPageSizeAndSearchValues() {
        assertThatThrownBy(() -> query(-1, 25, null))
                .isInstanceOf(MembershipQueryValidationException.class);
        assertThatThrownBy(() -> query(0, 0, null))
                .isInstanceOf(MembershipQueryValidationException.class);
        assertThatThrownBy(() -> query(0, 101, null))
                .isInstanceOf(MembershipQueryValidationException.class);
        assertThatThrownBy(() -> query(0, 25, "x".repeat(121)))
                .isInstanceOf(MembershipQueryValidationException.class);
        assertThatThrownBy(() -> query(0, 25, "MEM-1\nMEM-2"))
                .isInstanceOf(MembershipQueryValidationException.class);
    }

    private static MembershipSearchQuery query(int page, int size, String search) {
        return new MembershipSearchQuery(
                search, null, null, null, page, size,
                MembershipSortField.CREATED_AT, MembershipSortDirection.DESC);
    }
}
