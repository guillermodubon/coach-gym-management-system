package io.github.guillermodubon.coachgym.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class StorageHealthIndicatorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void localStorageReadinessChecksExistingParentWithoutCreatingDirectories() {
        Path notCreated = temporaryDirectory.resolve("missing-artifact-root");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("gym.storage.provider", "local")
                .withProperty("gym.storage.access-credentials.directory", notCreated.resolve("credentials").toString())
                .withProperty("gym.storage.payment-receipts.directory", notCreated.resolve("receipts").toString())
                .withProperty("gym.storage.client-photos.directory", notCreated.resolve("photos").toString());
        StorageHealthIndicator indicator = new StorageHealthIndicator(environment, invalidSupabase());

        assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
        assertThat(Files.exists(notCreated)).isFalse();
    }

    @Test
    void invalidStorageConfigurationFailsReadinessWithoutExposingPathOrConfigurationValue() {
        String privateProviderName = "private-storage-setting";
        MockEnvironment unsupported = new MockEnvironment()
                .withProperty("gym.storage.provider", privateProviderName);
        StorageHealthIndicator unsupportedIndicator =
                new StorageHealthIndicator(unsupported, invalidSupabase());

        assertThat(unsupportedIndicator.health().getStatus().getCode()).isEqualTo("DOWN");
        assertThat(unsupportedIndicator.health().getDetails().toString())
                .doesNotContain(privateProviderName);

        String invalidDirectory = Character.toString((char) 0) + "private-path";
        MockEnvironment invalidPath = new MockEnvironment()
                .withProperty("gym.storage.provider", "local")
                .withProperty("gym.storage.access-credentials.directory", invalidDirectory)
                .withProperty("gym.storage.payment-receipts.directory", "data/receipts")
                .withProperty("gym.storage.client-photos.directory", "data/photos");
        StorageHealthIndicator invalidPathIndicator =
                new StorageHealthIndicator(invalidPath, invalidSupabase());

        assertThat(invalidPathIndicator.health().getStatus().getCode()).isEqualTo("DOWN");
        assertThat(invalidPathIndicator.health().getDetails().toString())
                .doesNotContain("private-path");
    }

    private static SupabaseStorageProperties invalidSupabase() {
        return new SupabaseStorageProperties(null, null, null,
                Duration.ofSeconds(5), Duration.ofSeconds(30));
    }
}
