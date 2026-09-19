package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class DatabaseConfigurationContractTest {

    private static final Path LOCAL_CONFIGURATION =
            Path.of("src/main/resources/application.yml");
    private static final Path SUPABASE_CONFIGURATION =
            Path.of("src/main/resources/application-supabase.yml");

    @Test
    void localRuntimePoolIsExternalizedAndBounded() throws Exception {
        String yaml = normalized(LOCAL_CONFIGURATION);

        assertThat(yaml)
                .contains("maximum-pool-size: ${database_max_pool_size:5}")
                .contains("minimum-idle: ${database_min_idle:1}")
                .contains("connection-timeout: ${database_connection_timeout:5000}")
                .contains("validation-timeout: ${database_validation_timeout:3000}")
                .contains("idle-timeout: ${database_idle_timeout:600000}")
                .contains("max-lifetime: ${database_max_lifetime:1800000}")
                .doesNotContain("maximum-pool-size: 0")
                .doesNotContain("maximum-pool-size: 20");
    }

    @Test
    void supabaseRuntimeAndMigrationConnectionsAreSeparatelyConfigurable() throws Exception {
        String yaml = normalized(SUPABASE_CONFIGURATION);

        assertThat(yaml)
                .contains("maximum-pool-size: ${database_max_pool_size:5}")
                .contains("url: ${supabase_flyway_jdbc_url:${supabase_jdbc_url}}")
                .contains("user: ${supabase_flyway_username:${supabase_db_username}}")
                .contains("password: ${supabase_flyway_password:${supabase_db_password}}")
                .contains("connect-retries: ${flyway_connect_retries:3}")
                .contains("connect-retries-interval: ${flyway_connect_retries_interval:pt5s}");
    }

    @Test
    void deployedProfileSelectsSharedStorageInsteadOfLocalDisk() throws Exception {
        String local = normalized(LOCAL_CONFIGURATION);
        String supabase = normalized(SUPABASE_CONFIGURATION);

        assertThat(local).contains("provider: ${storage_provider:local}");
        assertThat(supabase)
                .contains("provider: supabase")
                .contains("endpoint: ${supabase_storage_url}")
                .contains("service-key: ${supabase_service_role_key}");
    }

    private static String normalized(Path path) throws Exception {
        return Files.readString(path)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
