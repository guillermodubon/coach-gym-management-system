package io.github.guillermodubon.coachgym.auth.application;

import io.github.guillermodubon.coachgym.user.AuthenticationUserQuery;
import io.github.guillermodubon.coachgym.user.SuccessfulLoginRecorder;
import io.github.guillermodubon.coachgym.auth.infrastructure.security.LoginAttemptRateLimiter;
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
    private final LoginAttemptRateLimiter loginAttemptRateLimiter;

    public AuthenticationService(
            AuthenticationManager authenticationManager,
            AuthenticationUserQuery authenticationUserQuery,
            SuccessfulLoginRecorder successfulLoginRecorder,
            Clock clock,
            LoginAttemptRateLimiter loginAttemptRateLimiter) {
        this.authenticationManager = authenticationManager;
        this.authenticationUserQuery = authenticationUserQuery;
        this.successfulLoginRecorder = successfulLoginRecorder;
        this.clock = clock;
        this.loginAttemptRateLimiter = loginAttemptRateLimiter;
    }

    public Authentication authenticate(String identifier, String password) throws AuthenticationException {
        String normalizedIdentifier = identifier == null ? "" : identifier.trim();
        if (!loginAttemptRateLimiter.isAllowed(normalizedIdentifier)) {
            throw new RateLimitExceededException(
                    loginAttemptRateLimiter.retryAfterSeconds(normalizedIdentifier));
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(normalizedIdentifier, password));
        } catch (AuthenticationException exception) {
            loginAttemptRateLimiter.recordFailure(normalizedIdentifier);
            throw exception;
        }
        loginAttemptRateLimiter.clear(normalizedIdentifier);
        authenticationUserQuery.findActiveUserByIdentifier(authentication.getName())
                .ifPresent(user -> successfulLoginRecorder.recordSuccessfulLogin(user.id(), clock.instant()));
        return authentication;
    }
}
