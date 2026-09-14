package io.github.guillermodubon.coachgym.accesscredential;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AccessCredentialSchemaMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V23__create_access_credentials.sql");
    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("V(\\d+)__.*\\.sql");

    @Test
    void migrationDefinesCredentialAndAppendOnlyHistorySchema() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("create table gym.access_credentials")
                .contains("id uuid primary key")
                .contains("client_id uuid not null")
                .contains("token_fingerprint char(64) not null")
                .contains("storage_key varchar(500) not null")
                .contains("create table gym.access_credential_history")
                .contains("previous_status varchar(20)")
                .contains("changed_by_user_id uuid not null")
                .contains("create index idx_access_credential_history_credential_occurred_at")
                .contains("before update or delete on gym.access_credential_history")
                .doesNotContain("raw_token")
                .doesNotContain("bytea");
    }

    @Test
    void migrationProtectsCredentialLifecycleAndArtifactMetadata() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("uq_access_credentials_token_fingerprint unique")
                .contains("where status = 'active'")
                .contains("status in ('active', 'revoked')")
                .contains("content_type = 'image/png'")
                .contains("size_bytes > 0 and size_bytes <= 1048576")
                .contains("checksum_sha256 ~ '^[0-9a-f]{64}$'")
                .contains("version >= 0")
                .contains("new access credentials must start active")
                .contains("access credential lifecycle transition is invalid")
                .contains("access credentials cannot be deleted")
                .contains("access credential history is append-only");
    }

    @Test
    void migrationUsesRestrictiveRelationshipsAndReplacementValidation() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .containsSubsequence(
                        "foreign key (client_id)",
                        "references gym.clients (id)",
                        "on delete restrict")
                .containsSubsequence(
                        "foreign key (issued_by_user_id)",
                        "references gym.users (id)",
                        "on delete restrict")
                .containsSubsequence(
                        "foreign key (replaced_by_credential_id)",
                        "references gym.access_credentials (id)",
                        "on delete restrict")
                .contains("replacement credential must be an active credential of the same client")
                .contains("history replacement must be active and belong to the same client")
                .doesNotContain("on delete cascade");
    }

    @Test
    void migrationVersionsAreUniqueAndV23IsAvailable() throws Exception {
        try (Stream<Path> files = Files.list(MIGRATION.getParent())) {
            var versions = files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .map(path -> VERSIONED_MIGRATION.matcher(path.getFileName().toString()))
                    .filter(java.util.regex.Matcher::matches)
                    .map(matcher -> Integer.parseInt(matcher.group(1)))
                    .sorted(Comparator.naturalOrder())
                    .toList();

            assertThat(versions).doesNotHaveDuplicates();
            assertThat(versions).contains(23);
        }
    }

    private static String normalized() throws Exception {
        return Files.readString(MIGRATION)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
