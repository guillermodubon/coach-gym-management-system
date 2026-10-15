package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/** Fails closed unless the current persisted scope is an active organization administrator. */
final class OrganizationAdminActuatorAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    private final StaffScopeQuery staffScopeQuery;

    OrganizationAdminActuatorAuthorizationManager(StaffScopeQuery staffScopeQuery) {
        this.staffScopeQuery = Objects.requireNonNull(staffScopeQuery);
    }

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authentication,
            RequestAuthorizationContext context) {
        Authentication current = authentication.get();
        if (current == null
                || !current.isAuthenticated()
                || !(current.getPrincipal() instanceof CoachGymUserPrincipal principal)) {
            return new AuthorizationDecision(false);
        }

        try {
            boolean authorized = staffScopeQuery.findAuthorizationContext(principal.id())
                    .filter(staff -> staff.userId().equals(principal.id()))
                    .map(StaffAuthorizationContext::organizationAdmin)
                    .orElse(false);
            return new AuthorizationDecision(authorized);
        } catch (RuntimeException unavailable) {
            return new AuthorizationDecision(false);
        }
    }
}
