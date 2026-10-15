package io.github.guillermodubon.coachgym.configuration;

import io.github.guillermodubon.coachgym.shared.security.StaffIdentityAbuseLimits;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Environment-overridable abuse limits with conservative hard bounds. */
@Validated
@ConfigurationProperties(prefix = "coach-gym.security.identity-abuse")
public record StaffIdentityAbuseProperties(
        int maxInvitationCreationsPerAdminPerHour,
        Duration invitationCreationWindow,
        int maxInvitationDeliveriesPerEmailPerDay,
        Duration invitationDeliveryWindow,
        Duration invitationResendCooldown,
        int maxRecoveryRequestsPerEmailPerHour,
        Duration recoveryEmailRequestWindow,
        int maxRecoveryRequestsPerIpPer15Minutes,
        Duration recoveryRequestIpWindow,
        int maxRecoveryRequestsGloballyPerHour,
        Duration recoveryGlobalWindow,
        int maxTokenFailuresPerFingerprint,
        Duration tokenFingerprintFailureWindow,
        int maxInvitationTokenFailuresPerIpPer15Minutes,
        int maxRecoveryTokenFailuresPerIpPer15Minutes,
        Duration tokenIpFailureWindow,
        int maxAdminReauthenticationFailuresPerActorPer15Minutes,
        Duration adminReauthenticationFailureWindow)
        implements StaffIdentityAbuseLimits {

    public StaffIdentityAbuseProperties {
        Objects.requireNonNull(invitationCreationWindow);
        Objects.requireNonNull(invitationDeliveryWindow);
        Objects.requireNonNull(invitationResendCooldown);
        Objects.requireNonNull(recoveryEmailRequestWindow);
        Objects.requireNonNull(recoveryRequestIpWindow);
        Objects.requireNonNull(recoveryGlobalWindow);
        Objects.requireNonNull(tokenFingerprintFailureWindow);
        Objects.requireNonNull(tokenIpFailureWindow);
        Objects.requireNonNull(adminReauthenticationFailureWindow);
        requireCount(maxInvitationCreationsPerAdminPerHour, 1000);
        requireCount(maxInvitationDeliveriesPerEmailPerDay, 100);
        requireCount(maxRecoveryRequestsPerEmailPerHour, 100);
        requireCount(maxRecoveryRequestsPerIpPer15Minutes, 1000);
        requireCount(maxRecoveryRequestsGloballyPerHour, 100_000);
        requireCount(maxTokenFailuresPerFingerprint, 100);
        requireCount(maxInvitationTokenFailuresPerIpPer15Minutes, 1000);
        requireCount(maxRecoveryTokenFailuresPerIpPer15Minutes, 1000);
        requireCount(maxAdminReauthenticationFailuresPerActorPer15Minutes, 100);
        requireWindow(invitationCreationWindow, Duration.ofDays(1));
        requireWindow(invitationDeliveryWindow, Duration.ofDays(7));
        requireWindow(invitationResendCooldown, Duration.ofHours(1));
        requireWindow(recoveryEmailRequestWindow, Duration.ofDays(1));
        requireWindow(recoveryRequestIpWindow, Duration.ofDays(1));
        requireWindow(recoveryGlobalWindow, Duration.ofDays(1));
        requireWindow(tokenFingerprintFailureWindow, Duration.ofDays(2));
        requireWindow(tokenIpFailureWindow, Duration.ofDays(1));
        requireWindow(adminReauthenticationFailureWindow, Duration.ofDays(1));
    }

    public static StaffIdentityAbuseProperties defaults() {
        return new StaffIdentityAbuseProperties(
                20, Duration.ofHours(1),
                3, Duration.ofHours(24), Duration.ofMinutes(5),
                3, Duration.ofHours(1),
                10, Duration.ofMinutes(15),
                100, Duration.ofHours(1),
                5, Duration.ofHours(48),
                10, 10, Duration.ofMinutes(15),
                5, Duration.ofMinutes(15));
    }

    private static void requireCount(int count, int maximum) {
        if (count < 1 || count > maximum) {
            throw new IllegalArgumentException("Identity abuse limit is outside the allowed bounds.");
        }
    }

    private static void requireWindow(Duration window, Duration maximum) {
        if (window == null || window.isZero() || window.isNegative() || window.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("Identity abuse window is outside the allowed bounds.");
        }
    }
}
