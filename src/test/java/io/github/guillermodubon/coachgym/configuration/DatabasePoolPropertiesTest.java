package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DatabasePoolPropertiesTest {

    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration VALIDATION_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration MAX_LIFETIME = Duration.ofMinutes(30);

    @Test
    void acceptsTheSmallDefaultFreeTierPool() {
        DatabasePoolProperties properties = valid(5, 1);

        assertThat(properties.maximumPoolSize()).isEqualTo(5);
        assertThat(properties.minimumIdle()).isEqualTo(1);
        assertThat(properties.connectionTimeout()).isEqualTo(CONNECTION_TIMEOUT);
        assertThat(properties.validationTimeout()).isEqualTo(VALIDATION_TIMEOUT);
    }

    @Test
    void rejectsAnUnboundedOrInvertedPool() {
        assertThatThrownBy(() -> valid(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> valid(11, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> valid(5, 6))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsafeTimeoutRelationships() {
        assertThatThrownBy(() -> new DatabasePoolProperties(
                5,
                1,
                Duration.ofMillis(100),
                VALIDATION_TIMEOUT,
                IDLE_TIMEOUT,
                MAX_LIFETIME))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new DatabasePoolProperties(
                5,
                1,
                CONNECTION_TIMEOUT,
                Duration.ofSeconds(6),
                IDLE_TIMEOUT,
                MAX_LIFETIME))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new DatabasePoolProperties(
                5,
                1,
                CONNECTION_TIMEOUT,
                VALIDATION_TIMEOUT,
                Duration.ofSeconds(5),
                Duration.ofSeconds(4)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static DatabasePoolProperties valid(int maximumPoolSize, int minimumIdle) {
        return new DatabasePoolProperties(
                maximumPoolSize,
                minimumIdle,
                CONNECTION_TIMEOUT,
                VALIDATION_TIMEOUT,
                IDLE_TIMEOUT,
                MAX_LIFETIME);
    }
}
