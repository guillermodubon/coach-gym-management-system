package io.github.guillermodubon.coachgym.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AccessBranchOwnershipSchemaMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V35__add_access_attempt_branch_ownership.sql");
    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("V(\\d+)__.*\\.sql");

    @Test
    void addsImmutableRequiredBranchOwnershipWithRestrictiveIntegrity() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("alter table gym.access_records")
                .contains("add column branch_id uuid")
                .contains("alter column branch_id set not null")
                .contains("fk_access_records_branch")
                .contains("references gym.gym_branches (id)")
                .contains("on delete restrict")
                .contains("trg_access_records_reject_branch_mutation")
                .contains("access attempt branch ownership is immutable")
                .contains("immutable physical branch where the access attempt was processed")
                .doesNotContain("delete from gym.access_records");
    }

    @Test
    void backfillsHistoricalAttemptsToTheCanonicalInitialBranch() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("exactly one active canonical initial branch")
                .contains("update gym.access_records")
                .contains("set branch_id = initial_branch_id")
                .contains("drop trigger trg_access_records_append_only on gym.access_records")
                .contains("create trigger trg_access_records_append_only")
                .contains("access-attempt ownership backfill left records without a branch");
    }

    @Test
    void providesBranchAwareDuplicateScanIndexAndUniqueNextVersion() throws Exception {
        String sql = normalized();
        assertThat(sql)
                .contains("idx_access_records_branch_source_result_occurred_at")
                .contains("branch_id")
                .contains("access_credential_id")
                .contains("identification_source")
                .contains("decision")
                .contains("occurred_at")
                .contains("id");

        try (Stream<Path> files = Files.list(MIGRATION.getParent())) {
            var versions = files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .map(path -> VERSIONED_MIGRATION.matcher(
                            path.getFileName().toString()))
                    .filter(java.util.regex.Matcher::matches)
                    .map(matcher -> Integer.parseInt(matcher.group(1)))
                    .sorted(Comparator.naturalOrder())
                    .toList();

            assertThat(versions).doesNotHaveDuplicates();
            assertThat(versions).contains(34, 35, 36, 37);
            assertThat(versions).isSortedAccordingTo(Comparator.naturalOrder());
            assertThat(versions.get(versions.size() - 1)).isEqualTo(37);
        }
    }

    private static String normalized() throws Exception {
        return Files.readString(MIGRATION)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
