package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffIdentityAuthorizationException;
import io.github.guillermodubon.coachgym.user.StaffScopeAuthorizationPolicy;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only organization-admin use cases for the staff directory. */
@Service
public class StaffDirectoryApplicationService {

    private final StaffScopeQuery scopeQuery;
    private final StaffDirectoryQuery directoryQuery;

    public StaffDirectoryApplicationService(
            StaffScopeQuery scopeQuery,
            StaffDirectoryQuery directoryQuery) {
        this.scopeQuery = Objects.requireNonNull(scopeQuery);
        this.directoryQuery = Objects.requireNonNull(directoryQuery);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public StaffDirectoryPage findPage(
            StaffDirectorySearchQuery query,
            AuthenticatedActor actor) {
        Objects.requireNonNull(query, "Directory query is required.");
        requireOrganizationAdministrator(actor);
        return directoryQuery.findPage(query);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public StaffDirectoryDetails findDetails(UUID targetUserId, AuthenticatedActor actor) {
        if (targetUserId == null) {
            throw new IllegalArgumentException("Staff identity is required.");
        }
        requireOrganizationAdministrator(actor);
        return directoryQuery.findById(targetUserId)
                .orElseThrow(StaffIdentityNotFoundException::new);
    }

    private void requireOrganizationAdministrator(AuthenticatedActor actor) {
        if (actor == null || actor.id() == null
                || actor.username() == null || actor.username().isBlank()) {
            throw new StaffIdentityAuthorizationException(
                    "An authenticated organization administrator is required.");
        }
        StaffAuthorizationContext actorContext;
        try {
            actorContext = scopeQuery.findAuthorizationContext(actor.id())
                    .orElseThrow(() -> new StaffIdentityAuthorizationException(
                            "An active organization administrator is required."));
        } catch (StaffScopeDataAccessException exception) {
            throw new StaffDirectoryUnavailableException(exception);
        }
        StaffScopeAuthorizationPolicy.requireOrganizationAdministrator(actorContext);
    }
}
