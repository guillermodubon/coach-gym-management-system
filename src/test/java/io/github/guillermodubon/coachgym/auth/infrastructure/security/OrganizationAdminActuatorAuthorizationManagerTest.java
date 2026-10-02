package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.user.AuthenticatedUser;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@ExtendWith(MockitoExtension.class)
class OrganizationAdminActuatorAuthorizationManagerTest {

    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000009801");

    @Mock private StaffScopeQuery staffScopeQuery;

    @Test
    void allowsOnlyTheCurrentActivePersistedOrganizationAdministrator() {
        when(staffScopeQuery.findAuthorizationContext(USER_ID))
                .thenReturn(Optional.of(context(USER_ID, Set.of(RoleCode.ADMIN),
                        StaffAccountStatus.ACTIVE, StaffScopeType.ORGANIZATION)));

        var decision = manager().authorize(
                () -> authenticatedPrincipal(), requestContext());

        assertThat(decision.isGranted()).isTrue();
        verify(staffScopeQuery).findAuthorizationContext(USER_ID);
    }

    @Test
    void deniesBranchAdministratorsReceptionistsAndMissingOrUnavailableScope() {
        OrganizationAdminActuatorAuthorizationManager manager = manager();
        when(staffScopeQuery.findAuthorizationContext(USER_ID))
                .thenReturn(Optional.of(context(USER_ID, Set.of(RoleCode.ADMIN),
                        StaffAccountStatus.ACTIVE, StaffScopeType.BRANCH)))
                .thenReturn(Optional.of(context(USER_ID, Set.of(RoleCode.RECEPTIONIST),
                        StaffAccountStatus.ACTIVE, StaffScopeType.BRANCH)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(context(USER_ID, Set.of(RoleCode.ADMIN),
                        StaffAccountStatus.SUSPENDED, StaffScopeType.ORGANIZATION)))
                .thenReturn(Optional.of(context(USER_ID, Set.of(RoleCode.ADMIN),
                        StaffAccountStatus.DEACTIVATED, StaffScopeType.ORGANIZATION)))
                .thenThrow(new IllegalStateException("private persistence detail"));

        for (int attempt = 0; attempt < 6; attempt++) {
            assertThat(manager.authorize(() -> authenticatedPrincipal(), requestContext()).isGranted())
                    .isFalse();
        }
    }

    @Test
    void deniesMismatchedScopeIdentityAndAnonymousPrincipals() {
        UUID otherId = UUID.fromString("00000000-0000-0000-0000-000000009802");
        when(staffScopeQuery.findAuthorizationContext(USER_ID))
                .thenReturn(Optional.of(context(otherId, Set.of(RoleCode.ADMIN),
                        StaffAccountStatus.ACTIVE, StaffScopeType.ORGANIZATION)));
        assertThat(manager().authorize(() -> authenticatedPrincipal(), requestContext()).isGranted())
                .isFalse();

        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "key", "anonymous", java.util.List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertThat(manager().authorize(() -> anonymous, requestContext()).isGranted()).isFalse();
        verify(staffScopeQuery, times(1)).findAuthorizationContext(USER_ID);
    }

    private OrganizationAdminActuatorAuthorizationManager manager() {
        return new OrganizationAdminActuatorAuthorizationManager(staffScopeQuery);
    }

    private static UsernamePasswordAuthenticationToken authenticatedPrincipal() {
        CoachGymUserPrincipal principal = CoachGymUserPrincipal.from(new AuthenticatedUser(
                USER_ID, "ops-admin", "not-a-real-hash", "Ops Admin",
                Set.of(RoleCode.ADMIN), 0, false));
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
    }

    private static StaffAuthorizationContext context(
            UUID userId,
            Set<RoleCode> roles,
            StaffAccountStatus status,
            StaffScopeType scope) {
        return new StaffAuthorizationContext(userId, roles, status, scope, Set.of());
    }

    private static RequestAuthorizationContext requestContext() {
        return new RequestAuthorizationContext(new MockHttpServletRequest());
    }
}
