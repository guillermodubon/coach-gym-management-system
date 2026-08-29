package io.github.guillermodubon.coachgym.access.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessResult;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Deterministic unit tests for {@link AccessPolicy}.
 *
 * <p>Each test corresponds to exactly one step of the approved denial
 * precedence chain or one boundary condition.</p>
 */
class AccessPolicyTest {

    // ── Fixed test data ───────────────────────────────────────────────────────

    private static final UUID CLIENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");

    private static final UUID PERIOD_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    /** A fixed "today" that sits comfortably inside a valid period. */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 15);

    private static final LocalDate PERIOD_STARTS = LocalDate.of(2026, 9, 1);
    private static final LocalDate PERIOD_ENDS   = LocalDate.of(2026, 9, 30);

    private static final LocalDate FREEZE_STARTS = LocalDate.of(2026, 9, 10);
    private static final LocalDate FREEZE_ENDS   = LocalDate.of(2026, 9, 20);

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Returns a builder pre-filled with a fully resolved, valid, active
     * membership context. Individual tests override only the fields relevant to
     * the scenario they exercise.
     */
    private AccessCheckInContext.Builder validActiveBuilder() {
        return AccessCheckInContext
                .builder(AccessIdentifier.of("MEM-000001"), TODAY)
                .clientId(CLIENT_ID)
                .clientCode("CLI-000001")
                .clientStatus("ACTIVE")
                .membershipId(MEMBERSHIP_ID)
                .membershipCode("MEM-000001")
                .membershipStatus("ACTIVE")
                .membershipPeriodId(PERIOD_ID)
                .periodStartsOn(PERIOD_STARTS)
                .periodEffectiveEndsOn(PERIOD_ENDS);
    }

    // ── Step 10: ACCESS_ALLOWED (baseline) ────────────────────────────────────

    @Test
    void allowsAccessWhenAllChecksPass() {
        AccessCheckInContext context = validActiveBuilder().build();

        AccessEvaluation result = AccessPolicy.evaluate(context);

        assertThat(result.result()).isEqualTo(AccessResult.ALLOWED);
        assertThat(result.reasonCode()).isEqualTo(AccessReasonCode.ACCESS_ALLOWED);
        assertThat(result.reason()).isNotBlank();
    }

    // ── Step 1: IDENTIFIER_NOT_FOUND ─────────────────────────────────────────

    @Test
    void deniesWhenBothClientAndMembershipAreNull() {
        AccessCheckInContext context = AccessCheckInContext
                .builder(AccessIdentifier.of("XYZ-000001"), TODAY)
                .build(); // clientId=null, membershipId=null

        AccessEvaluation result = AccessPolicy.evaluate(context);

        assertThat(result.result()).isEqualTo(AccessResult.DENIED);
        assertThat(result.reasonCode()).isEqualTo(AccessReasonCode.IDENTIFIER_NOT_FOUND);
    }

    @Test
    void identifierNotFoundPrecedesClientStatus() {
        // Even if we somehow had clientStatus set, if clientId is null
        // the identifier was not resolved — should still be NOT_FOUND.
        AccessCheckInContext context = AccessCheckInContext
                .builder(AccessIdentifier.of("XYZ-000001"), TODAY)
                .clientStatus("INACTIVE") // irrelevant — clientId is null
                .build();

        AccessEvaluation result = AccessPolicy.evaluate(context);

        assertThat(result.reasonCode()).isEqualTo(AccessReasonCode.IDENTIFIER_NOT_FOUND);
    }

    // ── Step 2: CLIENT_INACTIVE ───────────────────────────────────────────────

    @Test
    void deniesWhenClientIsInactive() {
        AccessCheckInContext context = AccessCheckInContext
                .builder(AccessIdentifier.of("CLI-000001"), TODAY)
                .clientId(CLIENT_ID)
                .clientCode("CLI-000001")
                .clientStatus("INACTIVE")
                .build(); // membershipId=null

        AccessEvaluation result = AccessPolicy.evaluate(context);

        assertThat(result.result()).isEqualTo(AccessResult.DENIED);
        assertThat(result.reasonCode()).isEqualTo(AccessReasonCode.CLIENT_INACTIVE);
    }

    @Test
    void clientInactivePrecedesMembershipNotFound() {
        // Client resolved but inactive; membership also missing — inactive wins.
        AccessCheckInContext context = AccessCheckInContext
                .builder(AccessIdentifier.of("CLI-000001"), TODAY)
                .clientId(CLIENT_ID)
                .clientCode("CLI-000001")
                .clientStatus("INACTIVE")
                // membershipId intentionally null
                .build();

        assertThat(AccessPolicy.evaluate(context).reasonCode())
                .isEqualTo(AccessReasonCode.CLIENT_INACTIVE);
    }

    // ── Step 3: MEMBERSHIP_NOT_FOUND ─────────────────────────────────────────

    @Test
    void deniesWhenNoCurrentMembershipFound() {
        AccessCheckInContext context = AccessCheckInContext
                .builder(AccessIdentifier.of("CLI-000001"), TODAY)
                .clientId(CLIENT_ID)
                .clientCode("CLI-000001")
                .clientStatus("ACTIVE")
                // membershipId intentionally null
                .build();

        AccessEvaluation result = AccessPolicy.evaluate(context);

        assertThat(result.result()).isEqualTo(AccessResult.DENIED);
        assertThat(result.reasonCode()).isEqualTo(AccessReasonCode.MEMBERSHIP_NOT_FOUND);
    }

    // ── Step 5: MEMBERSHIP_CANCELLED ─────────────────────────────────────────

    @Test
    void deniesWhenMembershipIsCancelled() {
        AccessCheckInContext context = validActiveBuilder()
                .membershipStatus("CANCELLED")
                .build();

        AccessEvaluation result = AccessPolicy.evaluate(context);

        assertThat(result.result()).isEqualTo(AccessResult.DENIED);
        assertThat(result.reasonCode()).isEqualTo(AccessReasonCode.MEMBERSHIP_CANCELLED);
    }

    @Test
    void cancelledPrecedesFrozen() {
        AccessCheckInContext context = validActiveBuilder()
                .membershipStatus("CANCELLED")
                .freezeStartsOn(FREEZE_STARTS)
                .freezePlannedEndsOn(FREEZE_ENDS)
                .build();

        assertThat(AccessPolicy.evaluate(context).reasonCode())
                .isEqualTo(AccessReasonCode.MEMBERSHIP_CANCELLED);
    }

    // ── Step 6: MEMBERSHIP_FROZEN ─────────────────────────────────────────────

    @Test
    void deniesWhenFrozenAndOperationalDateWithinFreezeWindow() {
        // TODAY = 2026-09-15, freeze 09-10 → 09-20 (inclusive)
        AccessCheckInContext context = validActiveBuilder()
                .membershipStatus("FROZEN")
                .freezeStartsOn(FREEZE_STARTS)
                .freezePlannedEndsOn(FREEZE_ENDS)
                .build();

        AccessEvaluation result = AccessPolicy.evaluate(context);

        assertThat(result.result()).isEqualTo(AccessResult.DENIED);
        assertThat(result.reasonCode()).isEqualTo(AccessReasonCode.MEMBERSHIP_FROZEN);
    }

    @Test
    void deniesOnExactFreezeStartDate() {
        // Freeze start is inclusive.
        AccessCheckInContext context = AccessCheckInContext
                .builder(AccessIdentifier.of("MEM-000001"), FREEZE_STARTS)
                .clientId(CLIENT_ID).clientCode("CLI-000001").clientStatus("ACTIVE")
                .membershipId(MEMBERSHIP_ID).membershipCode("MEM-000001")
                .membershipStatus("FROZEN")
                .membershipPeriodId(PERIOD_ID)
                .periodStartsOn(PERIOD_STARTS).periodEffectiveEndsOn(PERIOD_ENDS)
                .freezeStartsOn(FREEZE_STARTS)
                .freezePlannedEndsOn(FREEZE_ENDS)
                .build();

        assertThat(AccessPolicy.evaluate(context).reasonCode())
                .isEqualTo(AccessReasonCode.MEMBERSHIP_FROZEN);
    }

    @Test
    void deniesOnExactFreezePlannedEndDate() {
        // Freeze end is inclusive.
        AccessCheckInContext context = AccessCheckInContext
                .builder(AccessIdentifier.of("MEM-000001"), FREEZE_ENDS)
                .clientId(CLIENT_ID).clientCode("CLI-000001").clientStatus("ACTIVE")
                .membershipId(MEMBERSHIP_ID).membershipCode("MEM-000001")
                .membershipStatus("FROZEN")
                .membershipPeriodId(PERIOD_ID)
                .periodStartsOn(PERIOD_STARTS).periodEffectiveEndsOn(PERIOD_ENDS)
                .freezeStartsOn(FREEZE_STARTS)
                .freezePlannedEndsOn(FREEZE_ENDS)
                .build();

        assertThat(AccessPolicy.evaluate(context).reasonCode())
                .isEqualTo(AccessReasonCode.MEMBERSHIP_FROZEN);
    }

    @Test
    void allowsAccessWhenFrozenButDateIsAfterFreezePlannedEnd() {
        // Date is one day after freeze ends — falls through to period check.
        LocalDate dayAfterFreezeEnd = FREEZE_ENDS.plusDays(1);
        AccessCheckInContext context = AccessCheckInContext
                .builder(AccessIdentifier.of("MEM-000001"), dayAfterFreezeEnd)
                .clientId(CLIENT_ID).clientCode("CLI-000001").clientStatus("ACTIVE")
                .membershipId(MEMBERSHIP_ID).membershipCode("MEM-000001")
                .membershipStatus("FROZEN")
                .membershipPeriodId(PERIOD_ID)
                .periodStartsOn(PERIOD_STARTS).periodEffectiveEndsOn(PERIOD_ENDS)
                .freezeStartsOn(FREEZE_STARTS)
                .freezePlannedEndsOn(FREEZE_ENDS)
                .build();

        // Day after freeze end (2026-09-21) is still within the period
        // (ends 2026-09-30) → should be ALLOWED.
        assertThat(AccessPolicy.evaluate(context).result())
                .isEqualTo(AccessResult.ALLOWED);
    }

    @Test
    void deniesAsFrozenWhenStatusIsFrozenButFreezeDatesAreAbsent() {
        // Defensive: status is FROZEN but no freeze dates available.
        AccessCheckInContext context = validActiveBuilder()
                .membershipStatus("FROZEN")
                // freezeStartsOn and freezePlannedEndsOn left null
                .build();

        assertThat(AccessPolicy.evaluate(context).reasonCode())
                .isEqualTo(AccessReasonCode.MEMBERSHIP_FROZEN);
    }

    @Test
    void frozenPrecedesExpired() {
        // Status FROZEN; expired period — frozen takes precedence.
        LocalDate yesterday = TODAY.minusDays(1);
        AccessCheckInContext context = AccessCheckInContext
                .builder(AccessIdentifier.of("MEM-000001"), TODAY)
                .clientId(CLIENT_ID).clientCode("CLI-000001").clientStatus("ACTIVE")
                .membershipId(MEMBERSHIP_ID).membershipCode("MEM-000001")
                .membershipStatus("FROZEN")
                .membershipPeriodId(PERIOD_ID)
                .periodStartsOn(PERIOD_STARTS)
                .periodEffectiveEndsOn(yesterday) // period already ended
                .freezeStartsOn(FREEZE_STARTS)
                .freezePlannedEndsOn(FREEZE_ENDS) // today is in the freeze window
                .build();

        assertThat(AccessPolicy.evaluate(context).reasonCode())
                .isEqualTo(AccessReasonCode.MEMBERSHIP_FROZEN);
    }

    // ── Step 7: MEMBERSHIP_EXPIRED ────────────────────────────────────────────

    @Test
    void deniesWhenMembershipStatusIsExpired() {
        AccessCheckInContext context = validActiveBuilder()
                .membershipStatus("EXPIRED")
                .build();

        AccessEvaluation result = AccessPolicy.evaluate(context);

        assertThat(result.result()).isEqualTo(AccessResult.DENIED);
        assertThat(result.reasonCode()).isEqualTo(AccessReasonCode.MEMBERSHIP_EXPIRED);
    }

    @Test
    void expiredStatusPrecedesPeriodExpiredCheck() {
        // Even if the period is still valid, EXPIRED status wins.
        AccessCheckInContext context = validActiveBuilder()
                .membershipStatus("EXPIRED")
                .periodEffectiveEndsOn(TODAY.plusDays(10))
                .build();

        assertThat(AccessPolicy.evaluate(context).reasonCode())
                .isEqualTo(AccessReasonCode.MEMBERSHIP_EXPIRED);
    }

    // ── Step 8: MEMBERSHIP_PERIOD_EXPIRED ─────────────────────────────────────

    @Test
    void deniesWhenEffectiveEndDateIsBeforeOperationalDate() {
        LocalDate yesterday = TODAY.minusDays(1);
        AccessCheckInContext context = validActiveBuilder()
                .periodEffectiveEndsOn(yesterday)
                .build();

        AccessEvaluation result = AccessPolicy.evaluate(context);

        assertThat(result.result()).isEqualTo(AccessResult.DENIED);
        assertThat(result.reasonCode()).isEqualTo(AccessReasonCode.MEMBERSHIP_PERIOD_EXPIRED);
    }

    @Test
    void allowsAccessOnExactEffectiveEndDate() {
        // Effective end is inclusive.
        AccessCheckInContext context = AccessCheckInContext
                .builder(AccessIdentifier.of("MEM-000001"), PERIOD_ENDS)
                .clientId(CLIENT_ID).clientCode("CLI-000001").clientStatus("ACTIVE")
                .membershipId(MEMBERSHIP_ID).membershipCode("MEM-000001")
                .membershipStatus("ACTIVE")
                .membershipPeriodId(PERIOD_ID)
                .periodStartsOn(PERIOD_STARTS)
                .periodEffectiveEndsOn(PERIOD_ENDS)
                .build();

        assertThat(AccessPolicy.evaluate(context).result())
                .isEqualTo(AccessResult.ALLOWED);
    }

    @Test
    void deniesOnDayAfterEffectiveEndDate() {
        LocalDate dayAfterEnd = PERIOD_ENDS.plusDays(1);
        AccessCheckInContext context = AccessCheckInContext
                .builder(AccessIdentifier.of("MEM-000001"), dayAfterEnd)
                .clientId(CLIENT_ID).clientCode("CLI-000001").clientStatus("ACTIVE")
                .membershipId(MEMBERSHIP_ID).membershipCode("MEM-000001")
                .membershipStatus("ACTIVE")
                .membershipPeriodId(PERIOD_ID)
                .periodStartsOn(PERIOD_STARTS)
                .periodEffectiveEndsOn(PERIOD_ENDS)
                .build();

        assertThat(AccessPolicy.evaluate(context).reasonCode())
                .isEqualTo(AccessReasonCode.MEMBERSHIP_PERIOD_EXPIRED);
    }

    // ── Step 9: MEMBERSHIP_NOT_STARTED ────────────────────────────────────────

    @Test
    void deniesWhenPeriodStartDateIsAfterOperationalDate() {
        LocalDate tomorrow = TODAY.plusDays(1);
        AccessCheckInContext context = validActiveBuilder()
                .periodStartsOn(tomorrow)
                .periodEffectiveEndsOn(tomorrow.plusDays(30))
                .build();

        AccessEvaluation result = AccessPolicy.evaluate(context);

        assertThat(result.result()).isEqualTo(AccessResult.DENIED);
        assertThat(result.reasonCode()).isEqualTo(AccessReasonCode.MEMBERSHIP_NOT_STARTED);
    }

    @Test
    void allowsAccessOnExactStartDate() {
        // Period start is inclusive.
        AccessCheckInContext context = AccessCheckInContext
                .builder(AccessIdentifier.of("MEM-000001"), PERIOD_STARTS)
                .clientId(CLIENT_ID).clientCode("CLI-000001").clientStatus("ACTIVE")
                .membershipId(MEMBERSHIP_ID).membershipCode("MEM-000001")
                .membershipStatus("ACTIVE")
                .membershipPeriodId(PERIOD_ID)
                .periodStartsOn(PERIOD_STARTS)
                .periodEffectiveEndsOn(PERIOD_ENDS)
                .build();

        assertThat(AccessPolicy.evaluate(context).result())
                .isEqualTo(AccessResult.ALLOWED);
    }

    @Test
    void deniesOnDayBeforeStartDate() {
        LocalDate dayBeforeStart = PERIOD_STARTS.minusDays(1);
        AccessCheckInContext context = AccessCheckInContext
                .builder(AccessIdentifier.of("MEM-000001"), dayBeforeStart)
                .clientId(CLIENT_ID).clientCode("CLI-000001").clientStatus("ACTIVE")
                .membershipId(MEMBERSHIP_ID).membershipCode("MEM-000001")
                .membershipStatus("ACTIVE")
                .membershipPeriodId(PERIOD_ID)
                .periodStartsOn(PERIOD_STARTS)
                .periodEffectiveEndsOn(PERIOD_ENDS)
                .build();

        assertThat(AccessPolicy.evaluate(context).reasonCode())
                .isEqualTo(AccessReasonCode.MEMBERSHIP_NOT_STARTED);
    }

    @Test
    void periodExpiredPrecedesNotStarted() {
        // Both effectiveEnd < today AND start > today cannot coexist for a
        // valid period, but if effectiveEnd is checked first (it is), it wins.
        // This test documents the ordering.
        LocalDate longPast = TODAY.minusDays(100);
        LocalDate farFuture = TODAY.plusDays(100);
        AccessCheckInContext context = validActiveBuilder()
                .periodStartsOn(farFuture)
                .periodEffectiveEndsOn(longPast)
                .build();

        // effectiveEndsOn < today → PERIOD_EXPIRED (step 8 before step 9)
        assertThat(AccessPolicy.evaluate(context).reasonCode())
                .isEqualTo(AccessReasonCode.MEMBERSHIP_PERIOD_EXPIRED);
    }

    // ── Invariant violations (IllegalStateException) ──────────────────────────

    @Test
    void throwsWhenContextIsNull() {
        assertThatThrownBy(() -> AccessPolicy.evaluate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AccessCheckInContext must be provided.");
    }

    @Test
    void throwsWhenMembershipPresentButPeriodDataMissing() {
        // A non-cancelled membership with no period data is an invariant
        // violation; must not produce an access record.
        AccessCheckInContext context = AccessCheckInContext
                .builder(AccessIdentifier.of("MEM-000001"), TODAY)
                .clientId(CLIENT_ID)
                .clientCode("CLI-000001")
                .clientStatus("ACTIVE")
                .membershipId(MEMBERSHIP_ID)
                .membershipCode("MEM-000001")
                .membershipStatus("ACTIVE")
                // membershipPeriodId, periodStartsOn, periodEffectiveEndsOn all null
                .build();

        assertThatThrownBy(() -> AccessPolicy.evaluate(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("internal invariant violation");
    }

    @Test
    void throwsWhenFrozenMembershipHasNoPeriodDataAndDateIsAfterFreezeWindow() {
        // Status FROZEN, operational date is after freeze window,
        // but period data is missing → invariant violation.
        LocalDate dateAfterFreeze = FREEZE_ENDS.plusDays(1);
        AccessCheckInContext context = AccessCheckInContext
                .builder(AccessIdentifier.of("MEM-000001"), dateAfterFreeze)
                .clientId(CLIENT_ID).clientCode("CLI-000001").clientStatus("ACTIVE")
                .membershipId(MEMBERSHIP_ID).membershipCode("MEM-000001")
                .membershipStatus("FROZEN")
                // No period data
                .freezeStartsOn(FREEZE_STARTS)
                .freezePlannedEndsOn(FREEZE_ENDS)
                .build();

        assertThatThrownBy(() -> AccessPolicy.evaluate(context))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── Guard: null context ───────────────────────────────────────────────────

    @Test
    void rejectsNullOperationalDateInBuilder() {
        assertThatThrownBy(() ->
                AccessCheckInContext.builder(AccessIdentifier.of("MEM-000001"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Operational date must be provided.");
    }

    @Test
    void rejectsNullIdentifierInBuilder() {
        assertThatThrownBy(() ->
                AccessCheckInContext.builder(null, TODAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AccessIdentifier must be provided.");
    }

    // ── AccessEvaluation factory guards ───────────────────────────────────────

    @Test
    void allowedEvaluationHasCorrectConsistency() {
        AccessEvaluation evaluation = AccessEvaluation.allowed();
        assertThat(evaluation.result()).isEqualTo(AccessResult.ALLOWED);
        assertThat(evaluation.reasonCode()).isEqualTo(AccessReasonCode.ACCESS_ALLOWED);
        assertThat(evaluation.reason()).isNotBlank();
    }

    @Test
    void deniedEvaluationRejectsAccessAllowedCode() {
        assertThatThrownBy(() ->
                AccessEvaluation.denied(AccessReasonCode.ACCESS_ALLOWED, "some reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ACCESS_ALLOWED is not a valid denial reason code.");
    }

    @Test
    void deniedEvaluationRejectsNullCode() {
        assertThatThrownBy(() ->
                AccessEvaluation.denied(null, "some reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Denial reason code must be provided.");
    }

    @Test
    void deniedEvaluationRejectsBlankReason() {
        assertThatThrownBy(() ->
                AccessEvaluation.denied(AccessReasonCode.CLIENT_INACTIVE, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Denial reason text must be provided.");
    }

    // ── Identifier code formats accepted ─────────────────────────────────────

    @Test
    void acceptsMembershipCodeFormatInContext() {
        assertThatCode(() -> AccessCheckInContext
                .builder(AccessIdentifier.of("MEM-000001"), TODAY).build())
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsClientCodeFormatInContext() {
        assertThatCode(() -> AccessCheckInContext
                .builder(AccessIdentifier.of("CLI-000042"), TODAY).build())
                .doesNotThrowAnyException();
    }
}
