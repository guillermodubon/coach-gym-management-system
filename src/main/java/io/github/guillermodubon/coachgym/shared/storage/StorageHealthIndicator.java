package io.github.guillermodubon.coachgym.shared.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Reports storage readiness from local filesystem capability or Supabase
 * configuration without performing remote I/O and without creating files.
 */
@Component("storage")
public class StorageHealthIndicator implements HealthIndicator {

    private static final String DEFAULT_CREDENTIALS_DIRECTORY = "data/access-credentials";
    private static final String DEFAULT_RECEIPTS_DIRECTORY = "data/payment-receipts";
    private static final String DEFAULT_PHOTOS_DIRECTORY = "data/client-photos";

    private final Environment environment;
    private final SupabaseStorageProperties supabaseProperties;

    public StorageHealthIndicator(
            Environment environment,
            SupabaseStorageProperties supabaseProperties) {
        this.environment = environment;
        this.supabaseProperties = supabaseProperties;
    }

    @Override
    public Health health() {
        String provider = environment.getProperty(
                "gym.storage.provider", "local").strip().toLowerCase(Locale.ROOT);
        if ("supabase".equals(provider)) {
            return supabaseProperties.isValid()
                    ? Health.up().withDetail("provider", "supabase").build()
                    : Health.down()
                            .withDetail("provider", "supabase")
                            .withDetail("reason", "configuration_invalid")
                            .build();
        }
        if (!"local".equals(provider)) {
            return Health.down()
                    .withDetail("provider", provider)
                    .withDetail("reason", "unsupported_provider")
                    .build();
        }

        List<Path> roots = List.of(
                configuredPath("gym.storage.access-credentials.directory",
                        DEFAULT_CREDENTIALS_DIRECTORY),
                configuredPath("gym.storage.payment-receipts.directory",
                        DEFAULT_RECEIPTS_DIRECTORY),
                configuredPath("gym.storage.client-photos.directory",
                        DEFAULT_PHOTOS_DIRECTORY));
        boolean writable = roots.stream().allMatch(StorageHealthIndicator::parentIsWritable);
        return writable
                ? Health.up().withDetail("provider", "local").build()
                : Health.down()
                        .withDetail("provider", "local")
                        .withDetail("reason", "storage_path_not_writable")
                        .build();
    }

    private Path configuredPath(String key, String fallback) {
        return Path.of(environment.getProperty(key, fallback))
                .toAbsolutePath()
                .normalize();
    }

    private static boolean parentIsWritable(Path path) {
        Path candidate = path;
        while (candidate != null && !Files.exists(candidate)) {
            candidate = candidate.getParent();
        }
        return candidate != null && Files.isDirectory(candidate) && Files.isWritable(candidate);
    }
}
