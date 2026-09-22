package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class StaffProfileSchemaMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V30__create_staff_profile_photo_metadata.sql");
    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("V(\\d+)__.*\\.sql");

    @Test
    void createsPrivateMetadataWithoutBinaryPhotoStorage() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("create table gym.staff_profile_photos")
                .contains("user_id uuid not null")
                .contains("storage_key varchar(500) not null")
                .contains("checksum_sha256 char(64) not null")
                .contains("uq_staff_profile_photos_user")
                .contains("uq_staff_profile_photos_storage_key")
                .doesNotContain("bytea", "blob", "password_hash");
    }

    @Test
    void protectsOwnershipMetadataAndBounds() throws Exception {
        String sql = normalized();

        assertThat(sql)
                .contains("fk_staff_profile_photos_user")
                .contains("on delete restrict")
                .contains("fk_staff_profile_photos_created_by_user")
                .contains("fk_staff_profile_photos_updated_by_user")
                .contains("ck_staff_profile_photos_content_type")
                .contains("ck_staff_profile_photos_size")
                .contains("ck_staff_profile_photos_checksum_sha256")
                .contains("ck_staff_profile_photos_version_non_negative")
                .contains("trg_staff_profile_photos_set_updated_at");
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
            assertThat(versions).contains(29, 30, 31, 32);
            assertThat(versions)
                    .isSortedAccordingTo(Comparator.naturalOrder());
            assertThat(versions.get(versions.size() - 1))
                    .isEqualTo(32);
        }
    }

    private static String normalized() throws Exception {
        return Files.readString(MIGRATION)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
