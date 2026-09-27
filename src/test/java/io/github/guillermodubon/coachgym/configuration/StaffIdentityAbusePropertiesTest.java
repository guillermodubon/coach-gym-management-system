package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class StaffIdentityAbusePropertiesTest {

    @Test
    void defaultsMatchTheApprovedBoundedIdentityAbuseMatrix() {
        StaffIdentityAbuseProperties properties = StaffIdentityAbuseProperties.defaults();

        assertThat(properties.maxInvitationCreationsPerAdminPerHour()).isEqualTo(20);
        assertThat(properties.maxInvitationDeliveriesPerEmailPerDay()).isEqualTo(3);
        assertThat(properties.invitationResendCooldown()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.maxRecoveryRequestsPerEmailPerHour()).isEqualTo(3);
        assertThat(properties.maxRecoveryRequestsPerIpPer15Minutes()).isEqualTo(10);
        assertThat(properties.maxRecoveryRequestsGloballyPerHour()).isEqualTo(100);
        assertThat(properties.maxTokenFailuresPerFingerprint()).isEqualTo(5);
        assertThat(properties.maxInvitationTokenFailuresPerIpPer15Minutes()).isEqualTo(10);
        assertThat(properties.maxRecoveryTokenFailuresPerIpPer15Minutes()).isEqualTo(10);
        assertThat(properties.maxAdminReauthenticationFailuresPerActorPer15Minutes()).isEqualTo(5);
    }

    @Test
    void configuredCountersAndWindowsCannotBecomeUnbounded() {
        StaffIdentityAbuseProperties defaults = StaffIdentityAbuseProperties.defaults();

        assertThatThrownBy(() -> new StaffIdentityAbuseProperties(
                1001, defaults.invitationCreationWindow(),
                defaults.maxInvitationDeliveriesPerEmailPerDay(), defaults.invitationDeliveryWindow(),
                defaults.invitationResendCooldown(), defaults.maxRecoveryRequestsPerEmailPerHour(),
                defaults.recoveryEmailRequestWindow(), defaults.maxRecoveryRequestsPerIpPer15Minutes(),
                defaults.recoveryRequestIpWindow(), defaults.maxRecoveryRequestsGloballyPerHour(),
                defaults.recoveryGlobalWindow(), defaults.maxTokenFailuresPerFingerprint(),
                defaults.tokenFingerprintFailureWindow(), defaults.maxInvitationTokenFailuresPerIpPer15Minutes(),
                defaults.maxRecoveryTokenFailuresPerIpPer15Minutes(), defaults.tokenIpFailureWindow(),
                defaults.maxAdminReauthenticationFailuresPerActorPer15Minutes(),
                defaults.adminReauthenticationFailureWindow()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Identity abuse limit is outside the allowed bounds.");

        assertThatThrownBy(() -> new StaffIdentityAbuseProperties(
                defaults.maxInvitationCreationsPerAdminPerHour(), Duration.ZERO,
                defaults.maxInvitationDeliveriesPerEmailPerDay(), defaults.invitationDeliveryWindow(),
                defaults.invitationResendCooldown(), defaults.maxRecoveryRequestsPerEmailPerHour(),
                defaults.recoveryEmailRequestWindow(), defaults.maxRecoveryRequestsPerIpPer15Minutes(),
                defaults.recoveryRequestIpWindow(), defaults.maxRecoveryRequestsGloballyPerHour(),
                defaults.recoveryGlobalWindow(), defaults.maxTokenFailuresPerFingerprint(),
                defaults.tokenFingerprintFailureWindow(), defaults.maxInvitationTokenFailuresPerIpPer15Minutes(),
                defaults.maxRecoveryTokenFailuresPerIpPer15Minutes(), defaults.tokenIpFailureWindow(),
                defaults.maxAdminReauthenticationFailuresPerActorPer15Minutes(),
                defaults.adminReauthenticationFailureWindow()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Identity abuse window is outside the allowed bounds.");
    }
}
