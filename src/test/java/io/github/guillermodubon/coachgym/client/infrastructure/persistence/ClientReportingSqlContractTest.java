package io.github.guillermodubon.coachgym.client.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class ClientReportingSqlContractTest {

    @Test
    void aggregatesHomeBranchStatusAndRegistrationTimeWithoutSelectingPii() {
        String sql = normalized(JdbcClientReportingQuery.BRANCH_SQL);

        assertThat(sql)
                .contains("from gym.clients c")
                .contains("c.home_branch_id in (:branchids)")
                .contains("c.status = 'active'")
                .contains("c.status = 'inactive'")
                .contains("c.created_at >= :frominclusive")
                .contains("c.created_at < :toexclusive")
                .doesNotContain("first_name", "last_name", "email", "phone", "select *");
        assertThat(normalized(JdbcClientReportingQuery.ORGANIZATION_SQL))
                .doesNotContain(":branchids");
    }

    private static String normalized(String sql) {
        return sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
