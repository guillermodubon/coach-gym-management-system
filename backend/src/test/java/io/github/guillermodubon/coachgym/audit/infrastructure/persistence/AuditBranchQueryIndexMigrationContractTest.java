package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class AuditBranchQueryIndexMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V43__add_branch_scoped_audit_query_index.sql");

    @Test
    void addsOnlyTheEvidenceSupportedBranchAuditAccessPath() throws Exception {
        String sql = Files.readString(MIGRATION)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();

        assertThat(sql)
                .contains("create index idx_audit_entries_branch_occurred_at_id")
                .contains("on gym.audit_entries ((metadata ->> 'branchid'), occurred_at desc, id desc)")
                .contains("where metadata ->> 'branchid' is not null")
                .contains("comment on index gym.idx_audit_entries_branch_occurred_at_id")
                .doesNotContain("drop ", "alter table", "concurrently");
    }
}
