package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.user.AuthenticationUserQuery;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects stale or disabled staff principals before authorization or protected application work. */
final class AccountSecurityFreshnessFilter extends OncePerRequestFilter {

    private static final String PASSWORD_CHANGE_PATH = "/api/v1/me/profile/password";
    private static final String CURRENT_USER_PATH = "/api/v1/auth/me";
    private static final String CSRF_PATH = "/api/v1/auth/csrf";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";

    private final AuthenticationUserQuery users;
    private final AccessDeniedHandler accessDeniedHandler;

    AccountSecurityFreshnessFilter(
            AuthenticationUserQuery users,
            AccessDeniedHandler accessDeniedHandler) {
        this.users = Objects.requireNonNull(users);
        this.accessDeniedHandler = Objects.requireNonNull(accessDeniedHandler);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CoachGymUserPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        io.github.guillermodubon.coachgym.user.StaffAccountSecurityState current;
        try {
            current = users.findAccountSecurityState(principal.id()).orElse(null);
        } catch (RuntimeException unavailable) {
            invalidateSession(request);
            filterChain.doFilter(request, response);
            return;
        }
        if (current == null
                || current.status() != StaffAccountStatus.ACTIVE
                || current.securityVersion() != principal.securityVersion()
                || current.passwordChangeRequired() != principal.passwordChangeRequired()) {
            invalidateSession(request);
            filterChain.doFilter(request, response);
            return;
        }

        if (principal.passwordChangeRequired() && !passwordChangeAllowed(request)) {
            accessDeniedHandler.handle(
                    request,
                    response,
                    new AccessDeniedException("A password change is required before continuing."));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean passwordChangeAllowed(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return ("POST".equals(method) && PASSWORD_CHANGE_PATH.equals(path))
                || ("POST".equals(method) && LOGOUT_PATH.equals(path))
                || ("GET".equals(method) && (CURRENT_USER_PATH.equals(path) || CSRF_PATH.equals(path)));
    }

    private static void invalidateSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
