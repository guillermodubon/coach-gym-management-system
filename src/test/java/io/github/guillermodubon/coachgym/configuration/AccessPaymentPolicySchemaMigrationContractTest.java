package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AccessPaymentPolicySchemaMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V25__add_access_payment_policy_setting.sql");
    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("V(\\d+)__.*\\.sql");

    @Test
    void migrationAddsAFalseByDefaultNonNullPolicyColumn() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("alter table gym.gym_settings")
                .contains("add column require_confirmed_payment_for_access boolean not null default false")
                .doesNotContain("update gym.gym_settings")
                .doesNotContain("drop table gym.gym_settings");
    }

    @Test
    void migrationAllowsPaymentRequiredWithoutRewritingAccessHistory()
            throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("drop constraint ck_access_records_reason_code")
                .contains("add constraint ck_access_records_reason_code")
                .contains("'payment_required'")
                .doesNotContain("update gym.access_records")
                .doesNotContain("delete from gym.access_records");
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
assertThat(versions).contains(24, 25, 26, 27, 28, 29);
assertThat(versions)
        .isSortedAccordingTo(Comparator.naturalOrder());
assertThat(versions.get(versions.size() - 1))
        .isEqualTo(29);
        }
    }

    private static String normalized() throws Exception {
        return Files.readString(MIGRATION)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
