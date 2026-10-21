package io.github.guillermodubon.coachgym.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MembershipPeriodSearchQueryTest {

    @Test
    void defaultsDirectionAndKeepsPageBounds() {
        MembershipPeriodSearchQuery query = new MembershipPeriodSearchQuery(2, 10, null);

        assertThat(query.direction()).isEqualTo(MembershipSortDirection.DESC);
        assertThat(query.offset()).isEqualTo(20L);
    }

    @Test
    void rejectsNegativePageAndUnboundedPageSize() {
        assertThatThrownBy(() -> new MembershipPeriodSearchQuery(-1, 25, MembershipSortDirection.ASC))
                .isInstanceOf(MembershipQueryValidationException.class);
        assertThatThrownBy(() -> new MembershipPeriodSearchQuery(0, 0, MembershipSortDirection.ASC))
                .isInstanceOf(MembershipQueryValidationException.class);
        assertThatThrownBy(() -> new MembershipPeriodSearchQuery(0, 101, MembershipSortDirection.ASC))
                .isInstanceOf(MembershipQueryValidationException.class);
    }
}
