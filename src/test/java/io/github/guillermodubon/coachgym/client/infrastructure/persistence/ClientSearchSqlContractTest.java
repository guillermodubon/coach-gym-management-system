package io.github.guillermodubon.coachgym.client.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class ClientSearchSqlContractTest {

    @Test
    void searchUsesNamedParametersAndSupportedOperationalFields() {
        String sql =
                normalized(
                        JdbcClientSearchAdapter.FROM_AND_FILTERS);

        assertThat(sql)
                .contains("cast(:search as varchar) is null")
                .contains(
                        "lower(c.client_code) "
                                + "like cast(:searchpattern as varchar)")
                .contains(
                        "lower(c.first_name) "
                                + "like cast(:searchpattern as varchar)")
                .contains(
                        "lower(c.last_name) "
                                + "like cast(:searchpattern as varchar)")
                .contains(
                        "lower(concat_ws(' ', c.first_name, c.last_name)) "
                                + "like cast(:searchpattern as varchar)")
                .contains(
                        "lower(c.phone) "
                                + "like cast(:searchpattern as varchar)")
                .contains(
                        "lower(coalesce(c.email, '')) "
                                + "like cast(:searchpattern as varchar)")
                .contains("cast(:status as varchar) is null")
                .contains(
                        "c.status = cast(:status as varchar)")
                .contains(
                        "cast(:membershipstatus as varchar) is null")
                .contains(
                        "fm.status = "
                                + "cast(:membershipstatus as varchar)")
                .doesNotContain("select *");
    }

    @Test
    void profileUsesLatestLateralSnapshotsWithoutCollectionLoading() {
        String sql = normalized(JdbcClientOperationalProfileQuery.SQL);
        assertThat(sql)
                .contains("from gym.clients c")
                .contains("left join lateral")
                .contains("gym.emergency_contacts")
                .contains("gym.memberships")
                .contains("gym.membership_periods")
                .contains("gym.payments")
                .contains("gym.access_records")
                .contains("gym.client_photos")
                .contains("where c.id = :clientid")
                .doesNotContain("select *");
    }

    private static String normalized(String sql) {
        return sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
