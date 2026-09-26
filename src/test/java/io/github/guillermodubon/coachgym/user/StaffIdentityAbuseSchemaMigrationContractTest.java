package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class StaffIdentityAbuseSchemaMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V42__add_staff_identity_abuse_windows.sql");
    private static final Pattern VERSIONED_MIGRATION = Pattern.compile("V(\\d+)__.*\\.sql");

    @Test
    void addsShortLivedDistributedCountersWithoutRawOneTimeTokens() throws Exception {
        String sql = Files.readString(MIGRATION)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();

        assertThat(sql)
                .contains("create table gym.staff_identity_abuse_windows")
                .contains("primary key (bucket_type, subject_key)")
                .contains("attempt_count between 1 and 100001")
                .contains("expires_at > window_started_at")
                .contains("idx_staff_identity_abuse_expiration")
                .contains("recovery_email_request")
                .contains("invitation_token_failure")
                .contains("recovery_token_failure")
                .contains("high-entropy token fingerprint")
                .doesNotContain("raw_token", "password_hash", "create table gym.users");
    }

    @Test
    void migrationIsTheUniqueNextVersion() throws Exception {
        try (Stream<Path> files = Files.list(MIGRATION.getParent())) {
            var versions = files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .map(path -> VERSIONED_MIGRATION.matcher(path.getFileName().toString()))
                    .filter(java.util.regex.Matcher::matches)
                    .map(matcher -> Integer.parseInt(matcher.group(1)))
                    .sorted(Comparator.naturalOrder())
                    .toList();

            assertThat(versions).doesNotHaveDuplicates();
            assertThat(versions).isSortedAccordingTo(Comparator.naturalOrder());
            assertThat(versions.get(versions.size() - 1)).isEqualTo(42);
        }
    }
}
