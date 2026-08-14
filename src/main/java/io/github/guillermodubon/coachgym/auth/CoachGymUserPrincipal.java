package io.github.guillermodubon.coachgym.auth;

import io.github.guillermodubon.coachgym.user.AuthenticatedUser;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated staff identity kept in the server-side security context.
 *
 * <p>This is part of the authentication module contract because both its web and security
 * adapters need to read the authenticated identity.</p>
 */
public final class CoachGymUserPrincipal implements UserDetails {

    private final UUID id;
    private final String username;
    private final String passwordHash;
    private final String fullName;
    private final Collection<? extends GrantedAuthority> authorities;

    private CoachGymUserPrincipal(
            UUID id,
            String username,
            String passwordHash,
            String fullName,
            Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.authorities = List.copyOf(authorities);
    }

    public static CoachGymUserPrincipal from(AuthenticatedUser user) {
        List<GrantedAuthority> authorities = user.roles().stream()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
        return new CoachGymUserPrincipal(
                user.id(), user.username(), user.passwordHash(), user.fullName(), authorities);
    }

    public UUID id() {
        return id;
    }

    public String fullName() {
        return fullName;
    }

    public AuthenticatedActor authenticatedActor() {
        return new AuthenticatedActor(id, username);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
