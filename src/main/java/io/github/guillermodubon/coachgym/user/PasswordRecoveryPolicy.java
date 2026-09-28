package io.github.guillermodubon.coachgym.user;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Non-enumerating password-recovery behavior and expiration bounds. */
public final class PasswordRecoveryPolicy {

    public static final Duration DEFAULT_LIFETIME = Duration.ofMinutes(15);
    public static final Duration MAX_LIFETIME = Duration.ofMinutes(30);
    public static final String GENERIC_PUBLIC_RESPONSE =
            "If an eligible account exists, password recovery instructions will be sent.";

    private PasswordRecoveryPolicy() {
    }

    public static Duration requireLifetime(Duration lifetime) {
        Objects.requireNonNull(lifetime, "Recovery lifetime is required.");
        if (lifetime.isZero() || lifetime.isNegative() || lifetime.compareTo(MAX_LIFETIME) > 0) {
            throw new StaffIdentityValidationException(
                    "Password-recovery lifetime is outside the allowed bounds.");
        }
        return lifetime;
    }

    public static Instant requireExpiration(Instant createdAt, Instant expiresAt) {
        Objects.requireNonNull(createdAt, "Creation time is required.");
        Objects.requireNonNull(expiresAt, "Expiration time is required.");
        Duration lifetime = Duration.between(createdAt, expiresAt);
        requireLifetime(lifetime);
        return expiresAt;
    }

    /**
     * Every public recovery request receives this exact acknowledgement;
     * account eligibility is intentionally absent from the result.
     */
    public static String publicResponse(boolean recoveryEligible) {
        return GENERIC_PUBLIC_RESPONSE;
    }

    public static boolean mayIssueRecovery(StaffIdentityStatus status) {
        return status == StaffIdentityStatus.ACTIVE;
    }
}
