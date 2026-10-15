package io.github.guillermodubon.coachgym.access.domain;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import java.time.LocalDate;

/**
 * Stateless, deterministic access-check-in policy.
 *
 * <p>Call {@link #evaluate(AccessCheckInContext)} with a fully assembled
 * context. The method returns an {@link AccessEvaluation} for every normal
 * business case and throws {@link IllegalStateException} for internal
 * invariant violations that must not produce an access record.</p>
 *
 * <h2>Precedence (highest to lowest)</h2>
 * <ol>
 *   <li>Identifier not resolved → {@code IDENTIFIER_NOT_FOUND}</li>
 *   <li>Client is inactive → {@code CLIENT_INACTIVE}</li>
 *   <li>No current membership found → {@code MEMBERSHIP_NOT_FOUND}</li>
 *   <li>Ownership cross-check fails → {@link IllegalStateException}
 *       (never persisted)</li>
 *   <li>Membership is CANCELLED → {@code MEMBERSHIP_CANCELLED}</li>
 *   <li>Membership is FROZEN and operational date within freeze window
 *       → {@code MEMBERSHIP_FROZEN}</li>
 *   <li>Membership is EXPIRED → {@code MEMBERSHIP_EXPIRED}</li>
 *   <li>Period effective end date is before operational date
 *       → {@code MEMBERSHIP_PERIOD_EXPIRED}</li>
 *   <li>Period start date is after operational date
 *       → {@code MEMBERSHIP_NOT_STARTED}</li>
 *   <li>All checks pass → {@code ACCESS_ALLOWED}</li>
 * </ol>
 *
 * <p>Period start and effective end dates are <strong>inclusive</strong>:
 * a check-in on exactly those dates is valid.</p>
 *
 * <p>Freeze window boundaries are <strong>inclusive</strong>: a check-in on
 * {@code freeze.startsOn} or {@code freeze.plannedEndsOn} is denied as
 * frozen.</p>
 */
public final class AccessPolicy {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String STATUS_FROZEN = "FROZEN";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private AccessPolicy() {
    }

    /**
     * Evaluates access for the supplied context.
     *
     * @param context fully assembled context; must not be {@code null}
     * @return the evaluation result
     * @throws IllegalArgumentException  if {@code context} is null
     * @throws IllegalStateException     if an internal invariant is violated
     *                                   (ownership cross-check or missing
     *                                   period for a non-cancelled membership)
     */
    public static AccessEvaluation evaluate(AccessCheckInContext context) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "AccessCheckInContext must be provided.");
        }

        // ── Step 1: Identifier resolution ─────────────────────────────────────
        if (context.clientId() == null && context.membershipId() == null) {
            return AccessEvaluation.denied(
                    AccessReasonCode.IDENTIFIER_NOT_FOUND,
                    "The presented identifier could not be resolved "
                            + "to a known client or membership.");
        }

        // ── Step 2: Client status ─────────────────────────────────────────────
        if (STATUS_INACTIVE.equals(context.clientStatus())) {
            return AccessEvaluation.denied(
                    AccessReasonCode.CLIENT_INACTIVE,
                    "The client account is inactive.");
        }

        // ── Step 3: Current membership existence ──────────────────────────────
        if (context.membershipId() == null) {
            return AccessEvaluation.denied(
                    AccessReasonCode.MEMBERSHIP_NOT_FOUND,
                    "No active or frozen membership was found for this client.");
        }

        // ── Step 4: Ownership cross-check ─────────────────────────────────────
        // The application service performs this check before building the
        // context (membership.clientId() must equal resolved client.id()).
        // If the invariant was violated, the service throws IllegalStateException
        // before calling evaluate(). No policy code is needed here.

        String status = context.membershipStatus();

        // ── Step 5: Cancelled ─────────────────────────────────────────────────
        if (STATUS_CANCELLED.equals(status)) {
            return AccessEvaluation.denied(
                    AccessReasonCode.MEMBERSHIP_CANCELLED,
                    "The membership has been cancelled.");
        }

        // ── Step 6: Frozen (status + open freeze window, inclusive) ──────────
        if (STATUS_FROZEN.equals(status)) {
            return evaluateFrozen(context);
        }

        // ── Step 7: Expired ───────────────────────────────────────────────────
        if (STATUS_EXPIRED.equals(status)) {
            return AccessEvaluation.denied(
                    AccessReasonCode.MEMBERSHIP_EXPIRED,
                    "The membership has expired.");
        }

        // ── Steps 8–9 require a current period ────────────────────────────────
        requireCurrentPeriod(context);

        // ── Step 8: Period expired (effective end in the past) ────────────────
        if (context.periodEffectiveEndsOn()
                .isBefore(context.operationalDate())) {
            return AccessEvaluation.denied(
                    AccessReasonCode.MEMBERSHIP_PERIOD_EXPIRED,
                    "The current membership period has expired.");
        }

        // ── Step 9: Period not yet started ────────────────────────────────────
        if (context.periodStartsOn()
                .isAfter(context.operationalDate())) {
            return AccessEvaluation.denied(
                    AccessReasonCode.MEMBERSHIP_NOT_STARTED,
                    "The current membership period has not started yet.");
        }

        // ── Step 10: All checks pass ───────────────────────────────────────────
        return AccessEvaluation.allowed();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Evaluates a FROZEN membership.
     *
     * <p>If the operational date falls within the open freeze window
     * (inclusive boundaries), access is denied as frozen. If no open freeze
     * record is available despite the FROZEN status (data inconsistency),
     * the denial is still emitted — FROZEN status is authoritative.</p>
     */
    private static AccessEvaluation evaluateFrozen(
            AccessCheckInContext context) {

        LocalDate operationalDate = context.operationalDate();
        LocalDate freezeStartsOn = context.freezeStartsOn();
        LocalDate freezePlannedEndsOn = context.freezePlannedEndsOn();

        // If freeze dates are missing but status is FROZEN, deny defensively.
        if (freezeStartsOn == null || freezePlannedEndsOn == null) {
            return AccessEvaluation.denied(
                    AccessReasonCode.MEMBERSHIP_FROZEN,
                    "The membership is currently frozen.");
        }

        // Inclusive boundary check.
        boolean withinFreezeWindow =
                !operationalDate.isBefore(freezeStartsOn)
                        && !operationalDate.isAfter(freezePlannedEndsOn);

        if (withinFreezeWindow) {
            return AccessEvaluation.denied(
                    AccessReasonCode.MEMBERSHIP_FROZEN,
                    "The membership is currently frozen.");
        }

        // The operational date is outside the freeze window (e.g. past
        // plannedEndsOn but not yet reactivated). Treat as period evaluation.
        requireCurrentPeriod(context);

        if (context.periodEffectiveEndsOn()
                .isBefore(operationalDate)) {
            return AccessEvaluation.denied(
                    AccessReasonCode.MEMBERSHIP_PERIOD_EXPIRED,
                    "The current membership period has expired.");
        }

        if (context.periodStartsOn()
                .isAfter(operationalDate)) {
            return AccessEvaluation.denied(
                    AccessReasonCode.MEMBERSHIP_NOT_STARTED,
                    "The current membership period has not started yet.");
        }

        return AccessEvaluation.allowed();
    }

    /**
     * Asserts that period data is present. Throws {@link IllegalStateException}
     * if it is missing for a non-cancelled membership — this is an internal
     * invariant violation and must not produce an access record.
     */
    private static void requireCurrentPeriod(
            AccessCheckInContext context) {

        if (context.membershipPeriodId() == null
                || context.periodStartsOn() == null
                || context.periodEffectiveEndsOn() == null) {

            throw new IllegalStateException(
                    "Membership " + context.membershipId()
                            + " has no current period data. "
                            + "This is an internal invariant violation.");
        }
    }

}
