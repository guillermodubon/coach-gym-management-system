package io.github.guillermodubon.coachgym.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class QrAccessSchemaMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V24__support_qr_access_attempts.sql");
    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("V(\\d+)__.*\\.sql");

    @Test
    void migrationAddsSafeQrMetadataAndCredentialIntegrity() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("add column identification_source varchar(32) not null default 'unknown'")
                .contains("add column access_credential_id uuid")
                .contains("ck_access_records_identification_source")
                .contains("'qr_credential'")
                .contains("ck_access_records_qr_credential_metadata")
                .contains("fk_access_records_access_credential")
                .contains("references gym.access_credentials (id)")
                .contains("on delete restrict")
                .contains("validate_access_record_qr_metadata")
                .contains("trg_access_records_validate_qr_metadata")
                .doesNotContain("raw_token")
                .doesNotContain("raw_payload")
                .doesNotContain("qr_payload")
                .doesNotContain("token_value");
    }

    @Test
    void migrationAddsDuplicateReasonIndexAndAppendOnlyProtection() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("drop constraint ck_access_records_reason_code")
                .contains("'duplicate_check_in'")
                .contains("create index idx_access_records_qr_credential_result_occurred_at")
                .contains("access_credential_id, decision, occurred_at desc, id asc")
                .contains("where identification_source = 'qr_credential'")
                .contains("reject_access_record_mutation")
                .contains("trg_access_records_append_only")
                .contains("access records are append-only")
                .doesNotContain("payment")
                .doesNotContain("card_number")
                .doesNotContain("cvc");
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
            assertThat(versions).contains(24);
            assertThat(versions).isSortedAccordingTo(Comparator.naturalOrder());
        }
    }

    private static String normalized() throws Exception {
        return Files.readString(MIGRATION)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
