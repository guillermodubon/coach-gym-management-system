package io.github.guillermodubon.coachgym.organization.application;

import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.AuthenticatedActorProvider;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Checks the authenticated actor and current persisted authority for global administration. */
@Component("organizationAdministrationAuthorization")
public final class OrganizationAdministrationAuthorization {

    private final StaffScopeQuery scopeQuery;

    public OrganizationAdministrationAuthorization(StaffScopeQuery scopeQuery) {
        this.scopeQuery = Objects.requireNonNull(scopeQuery);
    }

    /** Pure method-security check; persistence is read inside the service transaction. */
    public boolean matchesPrincipal(Authentication authentication, AuthenticatedActor actor) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedActorProvider provider)
                || !validActor(actor)) {
            return false;
        }
        return actor.equals(provider.authenticatedActor());
    }

    public void requireOrganizationAdministrator(AuthenticatedActor actor) {
        if (!validActor(actor)) {
            throw denied();
        }

        Optional<StaffAuthorizationContext> current;
        try {
            current = scopeQuery.findAuthorizationContext(actor.id());
        } catch (RuntimeException exception) {
            throw new OrganizationAdministrationUnavailableException(exception);
        }
        if (current == null) {
            throw new OrganizationAdministrationUnavailableException(null);
        }
        if (current.filter(context -> actor.id().equals(context.userId())
                        && context.organizationAdmin()).isEmpty()) {
            throw denied();
        }
    }

    private static boolean validActor(AuthenticatedActor actor) {
        return actor != null && actor.id() != null
                && actor.username() != null && !actor.username().isBlank();
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("An active organization administrator is required.");
    }
}
