package io.github.guillermodubon.coachgym.auth.application;

import io.github.guillermodubon.coachgym.user.AuthenticationUserQuery;
import io.github.guillermodubon.coachgym.user.SuccessfulLoginRecorder;
import io.github.guillermodubon.coachgym.shared.RateLimitExceededException;
import java.time.Clock;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final AuthenticationUserQuery authenticationUserQuery;
    private final SuccessfulLoginRecorder successfulLoginRecorder;
    private final Clock clock;
    private final LoginAttemptGuard loginAttemptGuard;
    private final LoginOutcomeRecorder loginOutcomeRecorder;

    public AuthenticationService(
            AuthenticationManager authenticationManager,
            AuthenticationUserQuery authenticationUserQuery,
            SuccessfulLoginRecorder successfulLoginRecorder,
            Clock clock,
            LoginAttemptGuard loginAttemptGuard,
            LoginOutcomeRecorder loginOutcomeRecorder) {
        this.authenticationManager = authenticationManager;
        this.authenticationUserQuery = authenticationUserQuery;
        this.successfulLoginRecorder = successfulLoginRecorder;
        this.clock = clock;
        this.loginAttemptGuard = loginAttemptGuard;
        this.loginOutcomeRecorder = loginOutcomeRecorder;
    }

    public Authentication authenticate(String identifier, String password) throws AuthenticationException {
        String normalizedIdentifier = identifier == null ? "" : identifier.trim();
        if (!loginAttemptGuard.isAllowed(normalizedIdentifier)) {
            recordOutcome(LoginOutcomeRecorder.Outcome.RATE_LIMITED);
            throw new RateLimitExceededException(
                    loginAttemptGuard.retryAfterSeconds(normalizedIdentifier));
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(normalizedIdentifier, password));
        } catch (AuthenticationException exception) {
            loginAttemptGuard.recordFailure(normalizedIdentifier);
            recordOutcome(LoginOutcomeRecorder.Outcome.REJECTED);
            throw exception;
        }
        loginAttemptGuard.clear(normalizedIdentifier);
        authenticationUserQuery.findActiveUserByIdentifier(authentication.getName())
                .ifPresent(user -> successfulLoginRecorder.recordSuccessfulLogin(user.id(), clock.instant()));
        recordOutcome(LoginOutcomeRecorder.Outcome.SUCCESS);
        return authentication;
    }

    private void recordOutcome(LoginOutcomeRecorder.Outcome outcome) {
        try {
            loginOutcomeRecorder.record(outcome);
        } catch (RuntimeException ignored) {
            // Telemetry is best-effort and must not change authentication behavior.
        }
    }
}
