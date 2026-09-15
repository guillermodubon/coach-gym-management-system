package io.github.guillermodubon.coachgym.access.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DuplicateScanPolicyTest {

    private static final Instant PREVIOUS =
            Instant.parse("2026-09-14T15:00:00Z");
    private static final Instant CURRENT =
            Instant.parse("2026-09-14T15:00:29Z");
    private static final Duration WINDOW = Duration.ofSeconds(30);

    @Test
    void usesTheSuppliedWindowWithoutInventingAConfigurationDefault() {
        DuplicateScanPolicy policy = new DuplicateScanPolicy(WINDOW);

        assertThat(policy.window()).isEqualTo(WINDOW);
    }

    @Test
    void noPreviousEquivalentAttemptIsNotDuplicate() {
        assertThat(new DuplicateScanPolicy(WINDOW)
                .evaluate(CURRENT, null))
                .isEqualTo(DuplicateScanResult.NOT_DUPLICATE);
    }

    @Test
    void attemptJustInsideWindowIsDuplicate() {
        assertThat(new DuplicateScanPolicy(WINDOW)
                .evaluate(CURRENT, PREVIOUS))
                .isEqualTo(DuplicateScanResult.DUPLICATE);
    }

    @Test
    void exactWindowBoundaryIsNotDuplicate() {
        Instant boundary = PREVIOUS.plus(WINDOW);

        assertThat(new DuplicateScanPolicy(WINDOW)
                .evaluate(boundary, PREVIOUS))
                .isEqualTo(DuplicateScanResult.NOT_DUPLICATE);
    }

    @Test
    void attemptJustOutsideWindowIsNotDuplicate() {
        Instant outside = PREVIOUS.plus(WINDOW).plusNanos(1);

        assertThat(new DuplicateScanPolicy(WINDOW)
                .evaluate(outside, PREVIOUS))
                .isEqualTo(DuplicateScanResult.NOT_DUPLICATE);
    }

    @Test
    void aFuturePreviousTimestampDoesNotBlockTheCurrentAttempt() {
        assertThat(new DuplicateScanPolicy(WINDOW)
                .evaluate(PREVIOUS, CURRENT))
                .isEqualTo(DuplicateScanResult.NOT_DUPLICATE);
    }

    @Test
    void rejectsMissingOrNonPositiveWindow() {
        assertThatThrownBy(() -> new DuplicateScanPolicy(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate scan window must be positive.");
        assertThatThrownBy(() -> new DuplicateScanPolicy(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate scan window must be positive.");
        assertThatThrownBy(() -> new DuplicateScanPolicy(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate scan window must be positive.");
    }

    @Test
    void requiresCurrentServerTimestamp() {
        assertThatThrownBy(() -> new DuplicateScanPolicy(WINDOW)
                .evaluate(null, PREVIOUS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Current access timestamp must be provided.");
    }
}
