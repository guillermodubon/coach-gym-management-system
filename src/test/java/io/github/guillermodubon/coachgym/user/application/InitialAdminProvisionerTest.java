package io.github.guillermodubon.coachgym.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.configuration.InitialAdminProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class InitialAdminProvisionerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-12T18:00:00Z"), ZoneOffset.UTC);

    @Mock
    private UserAccountStore userAccountStore;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Captor
    private ArgumentCaptor<InitialAdministrator> administratorCaptor;

    @Test
    void provisionsAnAdministratorOnlyForAnEnabledEmptyRegistry() {
        InitialAdminProperties properties = enabledProperties();
        when(userAccountStore.hasAnyUsers()).thenReturn(false);
        when(passwordEncoder.encode("A-strong-password")).thenReturn("{bcrypt}encoded-password");

        InitialAdminProvisioner provisioner = new InitialAdminProvisioner(
                properties, userAccountStore, passwordEncoder, CLOCK);

        provisioner.provision();

        verify(userAccountStore).createInitialAdministrator(administratorCaptor.capture(), org.mockito.ArgumentMatchers.eq(CLOCK.instant()));
        assertThat(administratorCaptor.getValue())
                .isEqualTo(new InitialAdministrator(
                        "coach-admin",
                        "admin@coach-gym.local",
                        "{bcrypt}encoded-password",
                        "Coach",
                        "Administrator"));
    }

    @Test
    void doesNotProvisionWhenBootstrapIsDisabled() {
        InitialAdminProperties properties = new InitialAdminProperties(false, "", "", "", "", "");
        InitialAdminProvisioner provisioner = new InitialAdminProvisioner(
                properties, userAccountStore, passwordEncoder, CLOCK);

        provisioner.provision();

        verify(userAccountStore, never()).hasAnyUsers();
        verify(passwordEncoder, never()).encode(any());
        verify(userAccountStore, never()).createInitialAdministrator(any(), any());
    }

    @Test
    void doesNotOverwriteExistingStaffAccounts() {
        when(userAccountStore.hasAnyUsers()).thenReturn(true);
        InitialAdminProvisioner provisioner = new InitialAdminProvisioner(
                enabledProperties(), userAccountStore, passwordEncoder, CLOCK);

        provisioner.provision();

        verify(passwordEncoder, never()).encode(any());
        verify(userAccountStore, never()).createInitialAdministrator(any(), any());
    }

    private static InitialAdminProperties enabledProperties() {
        return new InitialAdminProperties(
                true,
                " coach-admin ",
                " admin@coach-gym.local ",
                "A-strong-password",
                " Coach ",
                " Administrator ");
    }
}
