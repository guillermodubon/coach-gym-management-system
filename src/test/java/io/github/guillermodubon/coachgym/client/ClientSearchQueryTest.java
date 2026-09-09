package io.github.guillermodubon.coachgym.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ClientSearchQueryTest {

    @Test
    void suppliesStableDefaults() {
        ClientSearchQuery query = ClientSearchQuery.defaults();
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(25);
        assertThat(query.sort()).isEqualTo(ClientSortField.LAST_NAME);
        assertThat(query.direction()).isEqualTo(ClientSortDirection.ASC);
    }

    @Test
    void normalizesOptionalSearchAndMembershipStatus() {
        ClientSearchQuery query = new ClientSearchQuery(
                "  Ana Lopez  ", null, " active ", 1, 20, null, null);
        assertThat(query.search()).isEqualTo("Ana Lopez");
        assertThat(query.membershipStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsInvalidPagination() {
        assertThatThrownBy(() -> new ClientSearchQuery(
                null, null, null, -1, 25, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientSearchQuery(
                null, null, null, 0, 101, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
