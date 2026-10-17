package io.github.guillermodubon.coachgym.client.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ClientMutationSqlContractTest {

    @Test
    void adapterUsesOptimisticLockingAndAppendOnlyHistory() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/guillermodubon/coachgym/client/"
                        + "infrastructure/persistence/JdbcClientMutationAdapter.java"));
        String normalized = source.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("\\s+", " ");

        assertThat(normalized)
                .contains("version = version + 1")
                .contains("and version = :expectedversion")
                .contains("insert into gym.client_status_history")
                .contains("deactivated_at = case")
                .doesNotContain("delete from gym.clients")
                .doesNotContain("update gym.memberships")
                .doesNotContain("update gym.payments");
    }
}
