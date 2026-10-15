package io.github.guillermodubon.coachgym.client.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.client.ClientSearchQuery;
import io.github.guillermodubon.coachgym.client.ClientSortDirection;
import io.github.guillermodubon.coachgym.client.ClientSortField;
import org.junit.jupiter.api.Test;

class ClientSortAllowlistTest {

    @Test
    void translatesEveryPublicSortFieldWithoutUsingClientInputAsSql() {
        for (ClientSortField field : ClientSortField.values()) {
            ClientSearchQuery query = new ClientSearchQuery(
                    null, null, null, 0, 25, field, ClientSortDirection.ASC);
            assertThat(JdbcClientSearchAdapter.orderBy(query))
                    .startsWith(" order by ")
                    .endsWith("c.id asc");
        }
    }

    @Test
    void defaultNameSortIsStableAndHumanFriendly() {
        assertThat(JdbcClientSearchAdapter.orderBy(ClientSearchQuery.defaults()))
                .isEqualTo(" order by c.last_name asc, c.first_name asc, c.id asc");
    }
}
