package io.github.guillermodubon.coachgym.organization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.AuthenticatedActorProvider;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class OrganizationAdministrationAuthorizationTest {

    private static final UUID ACTOR_ID = UUID.fromString("7b0bf7d5-5184-43d2-8f9a-200000000080");
    private static final AuthenticatedActor ACTOR = new AuthenticatedActor(ACTOR_ID, "organization-admin");

    @Mock
    private StaffScopeQuery scopeQuery;

    @Test
    void principalMustMatchBothIdentityFieldsAndBeAuthenticated() {
        var authorization = new OrganizationAdministrationAuthorization(scopeQuery);
        AuthenticatedActorProvider provider = () -> ACTOR;
        var current = new UsernamePasswordAuthenticationToken(provider, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThat(authorization.matchesPrincipal(current, ACTOR)).isTrue();
        assertThat(authorization.matchesPrincipal(current,
                new AuthenticatedActor(UUID.randomUUID(), ACTOR.username()))).isFalse();
        assertThat(authorization.matchesPrincipal(current,
                new AuthenticatedActor(ACTOR_ID, "other-user"))).isFalse();
        assertThat(authorization.matchesPrincipal(current, null)).isFalse();
        assertThat(authorization.matchesPrincipal(current,
                new AuthenticatedActor(ACTOR_ID, " "))).isFalse();
        assertThat(authorization.matchesPrincipal(null, ACTOR)).isFalse();
        assertThat(authorization.matchesPrincipal(
                new UsernamePasswordAuthenticationToken("generic-user", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))), ACTOR)).isFalse();
        verifyNoInteractions(scopeQuery);
    }

    @Test
    void currentOrganizationAdministratorCanEnterWithoutAssignment() {
        when(scopeQuery.findAuthorizationContext(ACTOR_ID)).thenReturn(Optional.of(
                context(ACTOR_ID, RoleCode.ADMIN, StaffAccountStatus.ACTIVE,
                        StaffScopeType.ORGANIZATION)));

        new OrganizationAdministrationAuthorization(scopeQuery)
                .requireOrganizationAdministrator(ACTOR);

        verify(scopeQuery).findAuthorizationContext(ACTOR_ID);
    }

    @Test
    void branchAdminReceptionistAndInactiveStaffAreDenied() {
        for (StaffAuthorizationContext context : List.of(
                context(ACTOR_ID, RoleCode.ADMIN, StaffAccountStatus.ACTIVE, StaffScopeType.BRANCH),
                context(ACTOR_ID, RoleCode.RECEPTIONIST, StaffAccountStatus.ACTIVE, StaffScopeType.BRANCH),
                context(ACTOR_ID, RoleCode.ADMIN, StaffAccountStatus.SUSPENDED, StaffScopeType.ORGANIZATION))) {
            when(scopeQuery.findAuthorizationContext(ACTOR_ID)).thenReturn(Optional.of(context));
            assertThatThrownBy(() -> new OrganizationAdministrationAuthorization(scopeQuery)
                    .requireOrganizationAdministrator(ACTOR))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void missingOrMismatchedAuthorityIsDeniedWithoutFallback() {
        var authorization = new OrganizationAdministrationAuthorization(scopeQuery);
        when(scopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(context(UUID.randomUUID(), RoleCode.ADMIN,
                        StaffAccountStatus.ACTIVE, StaffScopeType.ORGANIZATION)));

        assertThatThrownBy(() -> authorization.requireOrganizationAdministrator(ACTOR))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authorization.requireOrganizationAdministrator(ACTOR))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authorization.requireOrganizationAdministrator(
                new AuthenticatedActor(null, ACTOR.username())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void queryFailureIsUnavailableAndDoesNotLeakItsCause() {
        var authorization = new OrganizationAdministrationAuthorization(scopeQuery);
        when(scopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenThrow(new IllegalStateException("private SQL diagnostic"))
                .thenReturn(null);

        assertThatThrownBy(() -> authorization.requireOrganizationAdministrator(ACTOR))
                .isInstanceOf(OrganizationAdministrationUnavailableException.class)
                .hasMessage("Administrative authority could not be verified.")
                .hasMessageNotContaining("SQL");
        assertThatThrownBy(() -> authorization.requireOrganizationAdministrator(ACTOR))
                .isInstanceOf(OrganizationAdministrationUnavailableException.class);
    }

    private static StaffAuthorizationContext context(UUID id, RoleCode role,
            StaffAccountStatus status, StaffScopeType scope) {
        return new StaffAuthorizationContext(id, Set.of(role), status, scope, Set.of());
    }
}
