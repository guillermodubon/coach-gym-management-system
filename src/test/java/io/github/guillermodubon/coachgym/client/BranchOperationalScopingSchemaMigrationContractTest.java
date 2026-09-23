package io.github.guillermodubon.coachgym.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class BranchOperationalScopingSchemaMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V33__add_client_membership_branch_ownership.sql");
    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("V(\\d+)__.*\\.sql");

    @Test
    void addsImmutableOriginColumnsAndRestrictiveReferences() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("add column home_branch_id uuid")
                .contains("add column registered_at_branch_id uuid")
                .contains("alter column home_branch_id set not null")
                .contains("alter column registered_at_branch_id set not null")
                .contains("fk_clients_home_branch")
                .contains("fk_memberships_registered_at_branch")
                .contains("fk_membership_periods_registered_at_branch")
                .contains("on delete restrict")
                .doesNotContain("on delete cascade");
    }

    @Test
    void backfillsOnlyOwnershipToTheCanonicalInitialBranchBeforeValidation()
            throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("organization.is_canonical")
                .contains("branch.is_initial_branch")
                .contains("update gym.clients")
                .contains("set home_branch_id = initial_branch_id")
                .contains("set registered_at_branch_id = client.home_branch_id")
                .contains("set registered_at_branch_id = membership.registered_at_branch_id")
                .contains("exactly one active canonical initial branch")
                .contains("operational ownership backfill left records without a branch")
                .contains("drop trigger trg_clients_set_updated_at on gym.clients")
                .contains("create trigger trg_clients_set_updated_at")
                .contains("drop trigger trg_memberships_set_updated_at on gym.memberships")
                .contains("create trigger trg_memberships_set_updated_at")
                .contains("drop trigger trg_membership_periods_set_updated_at on gym.membership_periods")
                .contains("create trigger trg_membership_periods_set_updated_at")
                .doesNotContain("delete from gym.clients")
                .doesNotContain("delete from gym.memberships")
                .doesNotContain("delete from gym.membership_periods");
    }

    @Test
    void providesBranchFilteredOperationalIndexesWithoutChangingHistory() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("idx_clients_home_branch_status")
                .contains("idx_memberships_registered_at_branch_status")
                .contains("idx_membership_periods_registered_at_branch_membership")
                .contains("immutable operational home branch")
                .contains("immutable origin snapshot")
                .contains("current cross-branch use policy is deferred");
    }

    @Test
    void migrationIsTheUniqueNextVersion() throws Exception {
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
            assertThat(versions).contains(31, 32, 33, 34, 35, 36);
            assertThat(versions).isSortedAccordingTo(Comparator.naturalOrder());
            assertThat(versions.get(versions.size() - 1)).isEqualTo(36);
        }
    }

    private static String normalized() throws Exception {
        return Files.readString(MIGRATION)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
