package io.github.guillermodubon.coachgym.access.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Immutable context assembled by the application service before policy
 * evaluation.
 *
 * <p>Resolution fields are nullable to express partial resolution:</p>
 * <ul>
 *   <li>If {@code clientId} is {@code null}, resolution stopped at
 *       identifier lookup.</li>
 *   <li>If {@code membershipId} is {@code null}, no current membership
 *       was found for the client.</li>
 *   <li>Freeze fields are {@code null} when no open freeze exists.</li>
 * </ul>
 *
 * <p>Use {@link #builder(AccessIdentifier, LocalDate)} to construct.</p>
 */
public final class AccessCheckInContext {

    // ── Identifier ────────────────────────────────────────────────────────────

    private final AccessIdentifier identifier;

    // ── Operational date ──────────────────────────────────────────────────────

    private final LocalDate operationalDate;

    // ── Resolved client ───────────────────────────────────────────────────────

    /** Null when identifier was not resolved. */
    private final UUID clientId;

    /** Null when identifier was not resolved. */
    private final String clientCode;

    /**
     * Null when identifier was not resolved.
     * {@code "ACTIVE"} or {@code "INACTIVE"} when resolved.
     */
    private final String clientStatus;

    // ── Resolved membership ───────────────────────────────────────────────────

    /** Null when no current membership was found. */
    private final UUID membershipId;

    /** Null when no current membership was found. */
    private final String membershipCode;

    /**
     * Null when no current membership was found.
     * Matches {@code MembershipStatus.name()}.
     */
    private final String membershipStatus;

    // ── Resolved period ───────────────────────────────────────────────────────

    /** Null when no current membership was found. */
    private final UUID membershipPeriodId;

    /** Null when no current membership was found. */
    private final LocalDate periodStartsOn;

    /** Null when no current membership was found. */
    private final LocalDate periodEffectiveEndsOn;

    // ── Resolved freeze ───────────────────────────────────────────────────────

    /** Null when no open freeze exists for the membership. */
    private final LocalDate freezeStartsOn;

    /** Null when no open freeze exists for the membership. */
    private final LocalDate freezePlannedEndsOn;

    private AccessCheckInContext(Builder builder) {
        this.identifier = builder.identifier;
        this.operationalDate = builder.operationalDate;
        this.clientId = builder.clientId;
        this.clientCode = builder.clientCode;
        this.clientStatus = builder.clientStatus;
        this.membershipId = builder.membershipId;
        this.membershipCode = builder.membershipCode;
        this.membershipStatus = builder.membershipStatus;
        this.membershipPeriodId = builder.membershipPeriodId;
        this.periodStartsOn = builder.periodStartsOn;
        this.periodEffectiveEndsOn = builder.periodEffectiveEndsOn;
        this.freezeStartsOn = builder.freezeStartsOn;
        this.freezePlannedEndsOn = builder.freezePlannedEndsOn;
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    public AccessIdentifier identifier() { return identifier; }
    public LocalDate operationalDate() { return operationalDate; }
    public UUID clientId() { return clientId; }
    public String clientCode() { return clientCode; }
    public String clientStatus() { return clientStatus; }
    public UUID membershipId() { return membershipId; }
    public String membershipCode() { return membershipCode; }
    public String membershipStatus() { return membershipStatus; }
    public UUID membershipPeriodId() { return membershipPeriodId; }
    public LocalDate periodStartsOn() { return periodStartsOn; }
    public LocalDate periodEffectiveEndsOn() { return periodEffectiveEndsOn; }
    public LocalDate freezeStartsOn() { return freezeStartsOn; }
    public LocalDate freezePlannedEndsOn() { return freezePlannedEndsOn; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder(
            AccessIdentifier identifier,
            LocalDate operationalDate) {

        if (identifier == null) {
            throw new IllegalArgumentException(
                    "AccessIdentifier must be provided.");
        }
        if (operationalDate == null) {
            throw new IllegalArgumentException(
                    "Operational date must be provided.");
        }
        return new Builder(identifier, operationalDate);
    }

    public static final class Builder {

        private final AccessIdentifier identifier;
        private final LocalDate operationalDate;

        private UUID clientId;
        private String clientCode;
        private String clientStatus;

        private UUID membershipId;
        private String membershipCode;
        private String membershipStatus;

        private UUID membershipPeriodId;
        private LocalDate periodStartsOn;
        private LocalDate periodEffectiveEndsOn;

        private LocalDate freezeStartsOn;
        private LocalDate freezePlannedEndsOn;

        private Builder(
                AccessIdentifier identifier,
                LocalDate operationalDate) {

            this.identifier = identifier;
            this.operationalDate = operationalDate;
        }

        public Builder clientId(UUID clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder clientCode(String clientCode) {
            this.clientCode = clientCode;
            return this;
        }

        public Builder clientStatus(String clientStatus) {
            this.clientStatus = clientStatus;
            return this;
        }

        public Builder membershipId(UUID membershipId) {
            this.membershipId = membershipId;
            return this;
        }

        public Builder membershipCode(String membershipCode) {
            this.membershipCode = membershipCode;
            return this;
        }

        public Builder membershipStatus(String membershipStatus) {
            this.membershipStatus = membershipStatus;
            return this;
        }

        public Builder membershipPeriodId(UUID membershipPeriodId) {
            this.membershipPeriodId = membershipPeriodId;
            return this;
        }

        public Builder periodStartsOn(LocalDate periodStartsOn) {
            this.periodStartsOn = periodStartsOn;
            return this;
        }

        public Builder periodEffectiveEndsOn(LocalDate periodEffectiveEndsOn) {
            this.periodEffectiveEndsOn = periodEffectiveEndsOn;
            return this;
        }

        public Builder freezeStartsOn(LocalDate freezeStartsOn) {
            this.freezeStartsOn = freezeStartsOn;
            return this;
        }

        public Builder freezePlannedEndsOn(LocalDate freezePlannedEndsOn) {
            this.freezePlannedEndsOn = freezePlannedEndsOn;
            return this;
        }

        public AccessCheckInContext build() {
            return new AccessCheckInContext(this);
        }
    }
}
