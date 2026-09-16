package io.github.guillermodubon.coachgym.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class EmailDeliverySchemaMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/"
                    + "V26__create_transactional_email_delivery.sql");
    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("V(\\d+)__.*\\.sql");

    @Test
    void migrationDefinesCanonicalDeliveryAndAttemptTables() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("create table gym.email_deliveries")
                .contains("id uuid primary key")
                .contains("delivery_type varchar(32) not null")
                .contains("source_resource_id uuid not null")
                .contains("recipient_snapshot varchar(254) not null")
                .contains("idempotency_key_digest char(64) not null")
                .contains("create table gym.email_delivery_attempts")
                .contains("delivery_id uuid not null")
                .contains("attempt_number integer not null")
                .contains("provider_message_id varchar(200)")
                .contains("uq_email_delivery_attempts_delivery_number unique");
    }

    @Test
    void migrationProtectsLifecycleAttachmentAndFailureMetadata() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("delivery_type in ('payment_receipt', 'access_credential')")
                .contains("status in ('pending', 'sent', 'failed')")
                .contains("result in ('sent', 'failed', 'ambiguous')")
                .contains("recipient_snapshot = lower(btrim(recipient_snapshot))")
                .contains("attachment_resource_type = delivery_type")
                .contains("attachment_content_type = 'application/pdf'")
                .contains("attachment_content_type = 'image/png'")
                .contains("attachment_size_bytes > 0")
                .contains("attachment_size_bytes <= 10485760")
                .contains("attachment_checksum_sha256 ~ '^[0-9a-f]{64}$'")
                .contains("idempotency_key_digest ~ '^[0-9a-f]{64}$'")
                .contains("version >= 0")
                .contains("email delivery attempt count does not match its history")
                .contains("sent email deliveries are immutable")
                .doesNotContain("message_body")
                .doesNotContain("html_body")
                .doesNotContain("bytea");
    }

    @Test
    void migrationProtectsHistoryAndUsesRestrictiveRelationships() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .containsSubsequence(
                        "foreign key (client_id)",
                        "references gym.clients (id)",
                        "on delete restrict")
                .containsSubsequence(
                        "foreign key (requested_by_user_id)",
                        "references gym.users (id)",
                        "on delete restrict")
                .containsSubsequence(
                        "foreign key (delivery_id)",
                        "references gym.email_deliveries (id)",
                        "on delete restrict")
                .containsSubsequence(
                        "foreign key (attempted_by_user_id)",
                        "references gym.users (id)",
                        "on delete restrict")
                .contains("before update or delete on gym.email_delivery_attempts")
                .contains("email delivery attempts are append-only")
                .contains("email deliveries cannot be deleted")
                .doesNotContain("on delete cascade");
    }

    @Test
    void migrationUsesOneAvailableUniqueVersion() throws Exception {
        try (Stream<Path> files = Files.list(MIGRATION.getParent())) {
            var versions = files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .map(path -> VERSIONED_MIGRATION.matcher(path.getFileName().toString()))
                    .filter(java.util.regex.Matcher::matches)
                    .map(matcher -> Integer.parseInt(matcher.group(1)))
                    .sorted(Comparator.naturalOrder())
                    .toList();

            assertThat(versions).doesNotHaveDuplicates();
            assertThat(versions).contains(26);
        }
    }

    private static String normalized() throws Exception {
        return Files.readString(MIGRATION)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
