package io.github.guillermodubon.coachgym.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.user.AuthenticatedUser;
import io.github.guillermodubon.coachgym.user.AuthenticationUserQuery;
import io.github.guillermodubon.coachgym.user.RoleCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class CurrentPasswordVerificationServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString(
            "80000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    private AuthenticationUserQuery users;
    private PasswordEncoder encoder;
    private AdminReauthenticationAttemptStore attempts;
    private CurrentPasswordVerificationService service;

    @BeforeEach
    void setUp() {
        users = org.mockito.Mockito.mock(AuthenticationUserQuery.class);
        encoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        attempts = org.mockito.Mockito.mock(AdminReauthenticationAttemptStore.class);
        service = new CurrentPasswordVerificationService(
                users, encoder, attempts, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void validCurrentPasswordClearsOnlyThatActorsRecentFailures() {
        when(attempts.beginCheck(ACTOR_ID, NOW)).thenReturn(true);
        when(users.findActiveUserByIdentifier("admin-user"))
                .thenReturn(Optional.of(new AuthenticatedUser(
                        ACTOR_ID, "admin-user", "{bcrypt}stored-hash", "Admin", Set.of(RoleCode.ADMIN))));
        when(encoder.matches("current-secret", "{bcrypt}stored-hash")).thenReturn(true);

        assertThat(service.verify(ACTOR_ID, "admin-user", "current-secret")).isTrue();

        verify(attempts).clearFailures(ACTOR_ID);
        verify(attempts, never()).recordFailure(ACTOR_ID, NOW);
    }

    @Test
    void invalidPasswordIsCountedAndNeverAppearsInTheExceptionOrResult() {
        when(attempts.beginCheck(ACTOR_ID, NOW)).thenReturn(true);
        when(users.findActiveUserByIdentifier("admin-user"))
                .thenReturn(Optional.of(new AuthenticatedUser(
                        ACTOR_ID, "admin-user", "{bcrypt}stored-hash", "Admin", Set.of(RoleCode.ADMIN))));
        when(encoder.matches("wrong-secret", "{bcrypt}stored-hash")).thenReturn(false);

        assertThat(service.verify(ACTOR_ID, "admin-user", "wrong-secret")).isFalse();

        verify(attempts).recordFailure(ACTOR_ID, NOW);
        verify(attempts, never()).clearFailures(ACTOR_ID);
    }

    @Test
    void blockedActorDoesNotTriggerPasswordLookupOrEncoding() {
        when(attempts.beginCheck(ACTOR_ID, NOW)).thenReturn(false);

        assertThat(service.verify(ACTOR_ID, "admin-user", "candidate-secret")).isFalse();

        verifyNoInteractions(users, encoder);
        verify(attempts, never()).recordFailure(ACTOR_ID, NOW);
    }

    @Test
    void activeUserLookupMustResolveToTheSameActorId() {
        UUID otherId = UUID.fromString("80000000-0000-0000-0000-000000000002");
        when(attempts.beginCheck(ACTOR_ID, NOW)).thenReturn(true);
        when(users.findActiveUserByIdentifier("admin-user"))
                .thenReturn(Optional.of(new AuthenticatedUser(
                        otherId, "admin-user", "{bcrypt}stored-hash", "Other", Set.of(RoleCode.ADMIN))));

        assertThat(service.verify(ACTOR_ID, "admin-user", "candidate-secret")).isFalse();

        verify(encoder, never()).matches("candidate-secret", "{bcrypt}stored-hash");
        verify(attempts).recordFailure(ACTOR_ID, NOW);
    }
}
