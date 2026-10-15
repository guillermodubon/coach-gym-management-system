package io.github.guillermodubon.coachgym.configuration;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bounded JDBC pool settings for the application connection budget.
 *
 * <p>The defaults are intentionally small for the approved free-tier
 * deployment. The database remains the authority for cross-instance
 * correctness; this type only prevents an instance from consuming an
 * unbounded share of the database connections.
 */
@Validated
@ConfigurationProperties(prefix = "spring.datasource.hikari")
public record DatabasePoolProperties(
        int maximumPoolSize,
        int minimumIdle,
        Duration connectionTimeout,
        Duration validationTimeout,
        Duration idleTimeout,
        Duration maxLifetime) {

    public static final int DEFAULT_MAXIMUM_POOL_SIZE = 5;
    public static final int MAXIMUM_POOL_SIZE_LIMIT = 10;
    private static final Duration MINIMUM_TIMEOUT = Duration.ofMillis(250);
    private static final Duration MINIMUM_IDLE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration MINIMUM_MAX_LIFETIME = Duration.ofSeconds(30);

    public DatabasePoolProperties {
        Objects.requireNonNull(connectionTimeout, "Connection timeout is required.");
        Objects.requireNonNull(validationTimeout, "Validation timeout is required.");
        Objects.requireNonNull(idleTimeout, "Idle timeout is required.");
        Objects.requireNonNull(maxLifetime, "Maximum lifetime is required.");

        if (maximumPoolSize < 1 || maximumPoolSize > MAXIMUM_POOL_SIZE_LIMIT) {
            throw new IllegalArgumentException(
                    "Maximum pool size must be between 1 and "
                            + MAXIMUM_POOL_SIZE_LIMIT + ".");
        }
        if (minimumIdle < 0 || minimumIdle > maximumPoolSize) {
            throw new IllegalArgumentException(
                    "Minimum idle connections must be between 0 and the maximum pool size.");
        }
        if (connectionTimeout.compareTo(MINIMUM_TIMEOUT) < 0) {
            throw new IllegalArgumentException(
                    "Connection timeout must be at least 250 milliseconds.");
        }
        if (validationTimeout.compareTo(MINIMUM_TIMEOUT) < 0
                || validationTimeout.compareTo(connectionTimeout) > 0) {
            throw new IllegalArgumentException(
                    "Validation timeout must be at least 250 milliseconds and no greater than the connection timeout.");
        }
        if (idleTimeout.compareTo(MINIMUM_IDLE_TIMEOUT) < 0) {
            throw new IllegalArgumentException(
                    "Idle timeout must be at least 10 seconds.");
        }
        if (maxLifetime.compareTo(MINIMUM_MAX_LIFETIME) < 0
                || maxLifetime.compareTo(idleTimeout) < 0) {
            throw new IllegalArgumentException(
                    "Maximum lifetime must be at least 30 seconds and no less than the idle timeout.");
        }
    }
}
