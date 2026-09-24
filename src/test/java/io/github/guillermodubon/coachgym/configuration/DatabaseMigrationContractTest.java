package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DatabaseMigrationContractTest {

    private static final Path MIGRATIONS =
            Path.of("src/main/resources/db/migration");
    private static final Path ACCESS_DASHBOARD_MIGRATION = MIGRATIONS.resolve(
            "V28__optimize_access_dashboard_queries.sql");
    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("V(\\d+)__.*\\.sql");

    @Test
    void migrationChainHasUniqueContiguousVersionsThroughV37() throws Exception {
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            var versions = files
                    .filter(path -> VERSIONED_MIGRATION.matcher(
                            path.getFileName().toString()).matches())
                    .map(path -> VERSIONED_MIGRATION.matcher(
                            path.getFileName().toString()))
                    .map(matcher -> {
                        assertThat(matcher.matches()).isTrue();
                        return Integer.parseInt(matcher.group(1));
                    })
                    .sorted(Comparator.naturalOrder())
                    .toList();

            assertThat(versions).doesNotHaveDuplicates();
            assertThat(versions)
                    .containsExactlyElementsOf(
                                    java.util.stream.IntStream.rangeClosed(1, 37)
                                    .boxed()
                                    .toList());
        }
    }

    @Test
    void accessDashboardMigrationContainsOnlyTheEvidenceSupportedIndex() throws Exception {
        String sql = Files.readString(ACCESS_DASHBOARD_MIGRATION)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();

        assertThat(sql)
                .contains("create index idx_access_records_occurred_at_decision")
                .contains("on gym.access_records (occurred_at desc, decision)")
                .doesNotContain("drop ")
                .doesNotContain("alter table")
                .doesNotContain("if not exists");
    }
}
