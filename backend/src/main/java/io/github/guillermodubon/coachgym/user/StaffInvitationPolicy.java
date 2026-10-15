package io.github.guillermodubon.coachgym.user;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Pure role, scope, branch, authority, expiry, and resend policy for invitations. */
public final class StaffInvitationPolicy {

    public static final Duration ADMIN_INVITATION_LIFETIME = Duration.ofHours(24);
    public static final Duration RECEPTIONIST_INVITATION_LIFETIME = Duration.ofHours(48);
    public static final Duration RESEND_COOLDOWN = Duration.ofMinutes(5);
    public static final Duration DELIVERY_ATTEMPT_WINDOW = Duration.ofHours(24);
    public static final int MAX_INVITATION_CREATIONS_PER_ADMIN_PER_HOUR = 20;
    public static final int MAX_DELIVERY_ATTEMPTS_PER_EMAIL_PER_DAY = 3;
    public static final int MAX_INVITATION_BRANCHES = 100;

    private StaffInvitationPolicy() {
    }

    public static Duration defaultLifetime(RoleCode role) {
        Objects.requireNonNull(role, "Role is required.");
        return role == RoleCode.ADMIN
                ? ADMIN_INVITATION_LIFETIME
                : RECEPTIONIST_INVITATION_LIFETIME;
    }

    public static Set<UUID> normalizeBranchIds(Collection<UUID> branchIds) {
        if (branchIds == null) {
            throw new StaffIdentityValidationException("Proposed branches are required.");
        }
        if (branchIds.size() > MAX_INVITATION_BRANCHES) {
            throw new StaffIdentityValidationException(
                    "An invitation cannot include more than " + MAX_INVITATION_BRANCHES + " branches.");
        }
        TreeSet<UUID> sorted = new TreeSet<>();
        for (UUID branchId : branchIds) {
            if (branchId == null) {
                throw new StaffIdentityValidationException("Proposed branch is invalid.");
            }
            sorted.add(branchId);
            if (sorted.size() > MAX_INVITATION_BRANCHES) {
                throw new StaffIdentityValidationException(
                        "An invitation cannot include more than " + MAX_INVITATION_BRANCHES + " branches.");
            }
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }

    public static Set<UUID> requireValidProposal(
            RoleCode role,
            StaffScopeType scope,
            Collection<UUID> branchIds) {
        Objects.requireNonNull(role, "Role is required.");
        Objects.requireNonNull(scope, "Scope is required.");
        Set<UUID> branches = normalizeBranchIds(branchIds);
        StaffScopeAuthorizationPolicy.requireRoleScopeCombination(Set.of(role), scope);
        if (scope == StaffScopeType.ORGANIZATION && !branches.isEmpty()) {
            throw new StaffIdentityValidationException(
                    "Organization-scoped invitations cannot contain branch assignments.");
        }
        if (scope == StaffScopeType.BRANCH && branches.isEmpty()) {
            throw new StaffIdentityValidationException(
                    "Branch-scoped invitations require at least one branch assignment.");
        }
        return branches;
    }

    public static void requireAllBranchesActive(Set<UUID> proposedBranchIds, Set<UUID> activeBranchIds) {
        Set<UUID> proposed = normalizeBranchIds(proposedBranchIds);
        Set<UUID> active = normalizeBranchIds(activeBranchIds);
        if (!active.containsAll(proposed)) {
            throw new StaffIdentityStateConflictException(
                    "Every proposed branch must be active and belong to the organization.");
        }
    }

    /** Only an active organization administrator may manage any invitation. */
    public static void requireOrganizationAdministrator(StaffAuthorizationContext actor) {
        if (actor == null || !actor.organizationAdmin()) {
            throw new StaffIdentityAuthorizationException(
                    "Only an active organization administrator may manage staff invitations.");
        }
    }

    /**
     * Checks the complete inviter and target matrix using authoritative actor
     * facts and the active branch projection supplied by the organization
     * boundary. The reauthentication flag must be produced by an application
     * reauthentication check, never copied from a request.
     */
    public static void requireCanInvite(
            StaffAuthorizationContext actor,
            RoleCode invitedRole,
            StaffScopeType invitedScope,
            Collection<UUID> proposedBranchIds,
            Set<UUID> activeBranchIds,
            boolean currentPasswordReauthenticated) {
        requireOrganizationAdministrator(actor);
        Set<UUID> branches = requireValidProposal(invitedRole, invitedScope, proposedBranchIds);
        requireAllBranchesActive(branches, activeBranchIds);
        if (requiresReauthentication(invitedRole) && !currentPasswordReauthenticated) {
            throw new StaffIdentityAuthorizationException(
                    "Current-password reauthentication is required for an administrator invitation.");
        }
    }

    public static boolean requiresReauthentication(RoleCode invitedRole) {
        return Objects.requireNonNull(invitedRole, "Role is required.") == RoleCode.ADMIN;
    }

    public static Instant requireExpiration(
            RoleCode invitedRole,
            Instant createdAt,
            Instant expiresAt) {
        return requireExpiration(invitedRole, createdAt, createdAt, expiresAt);
    }

    public static Instant requireExpiration(
            RoleCode invitedRole,
            Instant createdAt,
            Instant lastSentAt,
            Instant expiresAt) {
        Objects.requireNonNull(createdAt, "Creation time is required.");
        Objects.requireNonNull(lastSentAt, "Last sent time is required.");
        Objects.requireNonNull(expiresAt, "Expiration time is required.");
        if (lastSentAt.isBefore(createdAt)) {
            throw new StaffIdentityValidationException(
                    "Invitation send time cannot precede its creation time.");
        }
        Duration lifetime = Duration.between(lastSentAt, expiresAt);
        if (lifetime.isZero() || lifetime.isNegative()
                || lifetime.compareTo(defaultLifetime(invitedRole)) > 0) {
            throw new StaffIdentityValidationException(
                    "Invitation expiration is outside the allowed lifetime.");
        }
        return expiresAt;
    }

    public static void requireAcceptable(
            StaffInvitationStatus status,
            Instant now,
            Instant expiresAt) {
        Objects.requireNonNull(now, "Current time is required.");
        Objects.requireNonNull(expiresAt, "Expiration time is required.");
        if (status != StaffInvitationStatus.PENDING || !now.isBefore(expiresAt)) {
            throw new StaffIdentityStateConflictException(
                    "Invitation is not pending or has expired.");
        }
    }

    public static void requireResendAllowed(
            StaffInvitationStatus status,
            Instant lastSentAt,
            Instant now) {
        requireResendAllowed(status, lastSentAt, now, RESEND_COOLDOWN);
    }

    public static void requireResendAllowed(
            StaffInvitationStatus status,
            Instant lastSentAt,
            Instant now,
            Duration resendCooldown) {
        Objects.requireNonNull(now, "Current time is required.");
        Objects.requireNonNull(resendCooldown, "Invitation resend cooldown is required.");
        if (resendCooldown.isNegative() || resendCooldown.isZero()) {
            throw new StaffIdentityValidationException("Invitation resend cooldown is invalid.");
        }
        if (status != StaffInvitationStatus.PENDING || lastSentAt == null) {
            throw new StaffIdentityStateConflictException(
                    "Only a previously sent pending invitation may be resent.");
        }
        if (now.isBefore(lastSentAt.plus(resendCooldown))) {
            throw new StaffIdentityStateConflictException(
                    "Invitation resend cooldown has not elapsed.");
        }
    }
}
