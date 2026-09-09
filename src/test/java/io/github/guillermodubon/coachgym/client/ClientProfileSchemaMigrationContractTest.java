package io.github.guillermodubon.coachgym.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ClientProfileSchemaMigrationContractTest {

    private static final String MIGRATION =
            "db/migration/V17__add_client_status_history_and_photo_metadata.sql";

    @Test
    void migrationDefinesAppendOnlyStatusHistory() throws IOException {
        String sql = normalized(migration());

        assertThat(sql)
                .contains("create table gym.client_status_history")
                .contains("previous_status varchar(20) not null")
                .contains("new_status varchar(20) not null")
                .contains("check (previous_status <> new_status)")
                .contains("check (btrim(reason) <> '')")
                .contains("reject_client_status_history_mutation")
                .contains("before update on gym.client_status_history")
                .contains("before delete on gym.client_status_history");
    }

    @Test
    void migrationDefinesOneValidatedPhotoPerClient() throws IOException {
        String sql = normalized(migration());

        assertThat(sql)
                .contains("create table gym.client_photos")
                .contains("unique (client_id)")
                .contains("unique (storage_key)")
                .contains("image/jpeg")
                .contains("image/png")
                .contains("image/webp")
                .contains("size_bytes <= 5242880")
                .contains("checksum_sha256")
                .contains("version >= 0")
                .doesNotContain("bytea");
    }

    @Test
    void migrationKeepsClientDeletionRestricted() throws IOException {
        String sql = normalized(migration());

        assertThat(sql)
                .containsSubsequence(
                        "references gym.clients (id)",
                        "on delete restrict");

        assertThat(sql)
                .doesNotContain("on delete cascade");

        assertThat(countOccurrences(
                sql,
                "references gym.clients (id)"))
                .isEqualTo(2);

        assertThat(countOccurrences(
                sql,
                "on delete restrict"))
                .isGreaterThanOrEqualTo(2);
    }

    private static int countOccurrences(
            String value,
            String fragment) {

        int count = 0;
        int index = 0;

        while ((index = value.indexOf(fragment, index)) >= 0) {
            count++;
            index += fragment.length();
        }

        return count;
    }

    private static String migration() throws IOException {
        try (var input = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(MIGRATION)) {
            if (input == null) {
                throw new IOException("Migration resource was not found: " + MIGRATION);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String normalized(String sql) {
        return sql.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("\s+", " ")
                .strip();
    }
}
