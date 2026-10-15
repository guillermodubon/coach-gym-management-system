package io.github.guillermodubon.coachgym.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.user.AuthenticationUserQuery;
import io.github.guillermodubon.coachgym.user.SuccessfulLoginRecorder;
import io.github.guillermodubon.coachgym.shared.RateLimitExceededException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private AuthenticationUserQuery users;
    @Mock private SuccessfulLoginRecorder successfulLoginRecorder;
    @Mock private LoginAttemptGuard loginAttemptGuard;
    @Mock private LoginOutcomeRecorder loginOutcomeRecorder;

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(
                authenticationManager,
                users,
                successfulLoginRecorder,
                Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC),
                loginAttemptGuard,
                loginOutcomeRecorder);
    }

    @Test
    void recordsSuccessfulOutcomeWithoutPassingIdentityToTelemetry() {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "staff-user", "credential", java.util.List.of());
        when(loginAttemptGuard.isAllowed("staff-user")).thenReturn(true);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(users.findActiveUserByIdentifier("staff-user")).thenReturn(Optional.empty());

        assertThat(service.authenticate(" staff-user ", "private-password")).isSameAs(authentication);

        verify(loginOutcomeRecorder).record(LoginOutcomeRecorder.Outcome.SUCCESS);
    }

    @Test
    void recordsRejectedAndRateLimitedOutcomesWithFiniteCategories() {
        when(loginAttemptGuard.isAllowed("staff-user")).thenReturn(true, false);
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("private authentication detail"));
        when(loginAttemptGuard.retryAfterSeconds("staff-user")).thenReturn(30L);

        assertThatThrownBy(() -> service.authenticate("staff-user", "private-password"))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.authenticate("staff-user", "private-password"))
                .isInstanceOf(RateLimitExceededException.class);

        verify(loginOutcomeRecorder).record(LoginOutcomeRecorder.Outcome.REJECTED);
        verify(loginOutcomeRecorder).record(LoginOutcomeRecorder.Outcome.RATE_LIMITED);
    }

    @Test
    void telemetryFailureDoesNotChangeAuthenticationResult() {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "staff-user", "credential", java.util.List.of());
        when(loginAttemptGuard.isAllowed("staff-user")).thenReturn(true);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(users.findActiveUserByIdentifier("staff-user")).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new IllegalStateException("private metric detail"))
                .when(loginOutcomeRecorder).record(LoginOutcomeRecorder.Outcome.SUCCESS);

        assertThat(service.authenticate("staff-user", "private-password")).isSameAs(authentication);
    }
}
