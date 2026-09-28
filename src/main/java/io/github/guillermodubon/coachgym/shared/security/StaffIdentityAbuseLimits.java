package io.github.guillermodubon.coachgym.shared.security;

import java.time.Duration;

/** Bounded identity-abuse limits shared by authentication and staff identity. */
public interface StaffIdentityAbuseLimits {

    default int maxInvitationCreationsPerAdminPerHour() { return 20; }

    default Duration invitationCreationWindow() { return Duration.ofHours(1); }

    default int maxInvitationDeliveriesPerEmailPerDay() { return 3; }

    default Duration invitationDeliveryWindow() { return Duration.ofHours(24); }

    default Duration invitationResendCooldown() { return Duration.ofMinutes(5); }

    default int maxRecoveryRequestsPerEmailPerHour() { return 3; }

    default Duration recoveryEmailRequestWindow() { return Duration.ofHours(1); }

    default int maxRecoveryRequestsPerIpPer15Minutes() { return 10; }

    default Duration recoveryRequestIpWindow() { return Duration.ofMinutes(15); }

    default int maxRecoveryRequestsGloballyPerHour() { return 100; }

    default Duration recoveryGlobalWindow() { return Duration.ofHours(1); }

    default int maxTokenFailuresPerFingerprint() { return 5; }

    default Duration tokenFingerprintFailureWindow() { return Duration.ofHours(48); }

    default int maxInvitationTokenFailuresPerIpPer15Minutes() { return 10; }

    default int maxRecoveryTokenFailuresPerIpPer15Minutes() { return 10; }

    default Duration tokenIpFailureWindow() { return Duration.ofMinutes(15); }

    default int maxAdminReauthenticationFailuresPerActorPer15Minutes() { return 5; }

    default Duration adminReauthenticationFailureWindow() { return Duration.ofMinutes(15); }

    static StaffIdentityAbuseLimits defaults() {
        return new StaffIdentityAbuseLimits() { };
    }
}
