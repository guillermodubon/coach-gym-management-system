package io.github.guillermodubon.coachgym.client.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ClientPhotoSqlContractTest {

    @Test
    void implementationUsesMetadataTableAndOptimisticDeletion() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/guillermodubon/coachgym/client/"
                        + "infrastructure/persistence/JdbcClientPhotoStore.java"))
                .toLowerCase(java.util.Locale.ROOT);
        assertThat(source)
                .contains("gym.client_photos")
                .contains("on conflict (client_id)")
                .contains("version = :expectedversion")
                .doesNotContain("bytea");
    }
}
