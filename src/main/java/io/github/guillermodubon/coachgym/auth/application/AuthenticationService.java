package io.github.guillermodubon.coachgym.auth.application;

import io.github.guillermodubon.coachgym.user.AuthenticationUserQuery;
import io.github.guillermodubon.coachgym.user.SuccessfulLoginRecorder;
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

    public AuthenticationService(
            AuthenticationManager authenticationManager,
            AuthenticationUserQuery authenticationUserQuery,
            SuccessfulLoginRecorder successfulLoginRecorder,
            Clock clock) {
        this.authenticationManager = authenticationManager;
        this.authenticationUserQuery = authenticationUserQuery;
        this.successfulLoginRecorder = successfulLoginRecorder;
        this.clock = clock;
    }

    public Authentication authenticate(String identifier, String password) throws AuthenticationException {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(identifier.trim(), password));
        authenticationUserQuery.findActiveUserByIdentifier(authentication.getName())
                .ifPresent(user -> successfulLoginRecorder.recordSuccessfulLogin(user.id(), clock.instant()));
        return authentication;
    }
}
