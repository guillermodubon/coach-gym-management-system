package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.user.AuthenticatedUser;
import io.github.guillermodubon.coachgym.user.AuthenticationUserQuery;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountSecurityState;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;

@ExtendWith(MockitoExtension.class)
class AccountSecurityFreshnessFilterTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000009301");

    @Mock private AuthenticationUserQuery users;
    @Mock private AccessDeniedHandler accessDeniedHandler;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void freshActivePrincipalContinuesToProtectedWork() throws Exception {
        authenticate(false, 4);
        when(users.findAccountSecurityState(USER_ID)).thenReturn(Optional.of(
                new StaffAccountSecurityState(StaffAccountStatus.ACTIVE, 4, false)));
        MockHttpServletRequest request = request("GET", "/api/v1/clients");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verifyNoInteractions(accessDeniedHandler);
    }

    @Test
    void staleAuthorityVersionInvalidatesTheWholeServerSideSessionBeforeProtectedWork() throws Exception {
        authenticate(false, 4);
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = request("GET", "/api/v1/clients");
        request.setSession(session);
        when(users.findAccountSecurityState(USER_ID)).thenReturn(Optional.of(
                new StaffAccountSecurityState(StaffAccountStatus.ACTIVE, 5, false)));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request, response, chain);

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void suspendedAccountCannotContinueWithAnOldSession() throws Exception {
        authenticate(false, 4);
        MockHttpServletRequest request = request("GET", "/api/v1/clients");
        when(users.findAccountSecurityState(USER_ID)).thenReturn(Optional.of(
                new StaffAccountSecurityState(StaffAccountStatus.SUSPENDED, 4, false)));
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(accessDeniedHandler);
    }

    @Test
    void bootstrapAccountMayOnlyInspectOwnStateChangePasswordRefreshCsrfOrLogout() throws Exception {
        authenticate(true, 0);
        when(users.findAccountSecurityState(USER_ID)).thenReturn(Optional.of(
                new StaffAccountSecurityState(StaffAccountStatus.ACTIVE, 0, true)));
        MockHttpServletRequest request = request("GET", "/api/v1/clients");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request, response, chain);

        verify(accessDeniedHandler).handle(org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.eq(response), org.mockito.ArgumentMatchers.any());
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void bootstrapPasswordChangeEndpointRemainsAvailable() throws Exception {
        authenticate(true, 0);
        when(users.findAccountSecurityState(USER_ID)).thenReturn(Optional.of(
                new StaffAccountSecurityState(StaffAccountStatus.ACTIVE, 0, true)));
        MockHttpServletRequest request = request("POST", "/api/v1/me/profile/password");
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
        verifyNoInteractions(accessDeniedHandler);
    }

    private AccountSecurityFreshnessFilter filter() {
        return new AccountSecurityFreshnessFilter(users, accessDeniedHandler);
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(path);
        return request;
    }

    private static void authenticate(boolean passwordChangeRequired, long version) {
        CoachGymUserPrincipal principal = CoachGymUserPrincipal.from(new AuthenticatedUser(
                USER_ID, "bootstrap-admin", "test-value-not-used", "Admin",
                Set.of(RoleCode.ADMIN), version, passwordChangeRequired));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
    }
}
