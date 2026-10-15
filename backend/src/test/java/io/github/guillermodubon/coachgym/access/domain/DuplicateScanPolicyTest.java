package io.github.guillermodubon.coachgym.access.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.access.AccessResult;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DuplicateScanPolicyTest {

    private static final Instant PREVIOUS =
            Instant.parse("2026-09-14T15:00:00Z");
    private static final Instant CURRENT =
            Instant.parse("2026-09-14T15:00:29Z");
    private static final Duration WINDOW = Duration.ofSeconds(30);
    private static final UUID CLIENT_ID = UUID.fromString(
            "70000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_CLIENT_ID = UUID.fromString(
            "70000000-0000-0000-0000-000000000002");
    private static final UUID BRANCH_ONE = UUID.fromString(
            "80000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_TWO = UUID.fromString(
            "80000000-0000-0000-0000-000000000002");

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

    @Test
    void sameClientSuccessfulAccessAtAnotherBranchIsDuplicateInsideWindow() {
        assertThat(new DuplicateScanPolicy(WINDOW).evaluateAcrossBranches(
                CLIENT_ID,
                BRANCH_TWO,
                CURRENT,
                CLIENT_ID,
                BRANCH_ONE,
                AccessResult.ALLOWED,
                PREVIOUS))
                .isEqualTo(DuplicateScanResult.DUPLICATE);
    }

    @Test
    void crossBranchWindowBoundaryIsHalfOpen() {
        assertThat(new DuplicateScanPolicy(WINDOW).evaluateAcrossBranches(
                CLIENT_ID,
                BRANCH_TWO,
                PREVIOUS.plus(WINDOW),
                CLIENT_ID,
                BRANCH_ONE,
                AccessResult.ALLOWED,
                PREVIOUS))
                .isEqualTo(DuplicateScanResult.NOT_DUPLICATE);
    }

    @Test
    void deniedDifferentClientAndSameBranchHistoryDoNotTriggerCrossBranchPolicy() {
        DuplicateScanPolicy policy = new DuplicateScanPolicy(WINDOW);

        assertThat(policy.evaluateAcrossBranches(
                CLIENT_ID, BRANCH_TWO, CURRENT, CLIENT_ID, BRANCH_ONE,
                AccessResult.DENIED, PREVIOUS))
                .isEqualTo(DuplicateScanResult.NOT_DUPLICATE);
        assertThat(policy.evaluateAcrossBranches(
                CLIENT_ID, BRANCH_TWO, CURRENT, OTHER_CLIENT_ID, BRANCH_ONE,
                AccessResult.ALLOWED, PREVIOUS))
                .isEqualTo(DuplicateScanResult.NOT_DUPLICATE);
        assertThat(policy.evaluateAcrossBranches(
                CLIENT_ID, BRANCH_ONE, CURRENT, CLIENT_ID, BRANCH_ONE,
                AccessResult.ALLOWED, PREVIOUS))
                .isEqualTo(DuplicateScanResult.NOT_DUPLICATE);
    }

    @Test
    void requiresCurrentCrossBranchIdentityAndCompletePriorAttempt() {
        DuplicateScanPolicy policy = new DuplicateScanPolicy(WINDOW);

        assertThatThrownBy(() -> policy.evaluateAcrossBranches(
                null, BRANCH_TWO, CURRENT, CLIENT_ID, BRANCH_ONE,
                AccessResult.ALLOWED, PREVIOUS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.evaluateAcrossBranches(
                CLIENT_ID, BRANCH_TWO, CURRENT, null, BRANCH_ONE,
                AccessResult.ALLOWED, PREVIOUS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.evaluateAcrossBranches(
                CLIENT_ID, BRANCH_TWO, CURRENT, CLIENT_ID, BRANCH_ONE,
                null, PREVIOUS))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
