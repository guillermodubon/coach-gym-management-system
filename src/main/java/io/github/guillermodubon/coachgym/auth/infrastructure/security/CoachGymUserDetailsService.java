package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.user.AuthenticationUserQuery;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
class CoachGymUserDetailsService implements UserDetailsService {

    private final AuthenticationUserQuery authenticationUserQuery;

    CoachGymUserDetailsService(AuthenticationUserQuery authenticationUserQuery) {
        this.authenticationUserQuery = authenticationUserQuery;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) {
        return authenticationUserQuery.findActiveUserByIdentifier(identifier)
                .map(CoachGymUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials."));
    }
}
