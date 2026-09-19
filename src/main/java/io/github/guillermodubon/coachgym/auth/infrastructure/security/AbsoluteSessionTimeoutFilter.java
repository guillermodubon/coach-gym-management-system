package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import io.github.guillermodubon.coachgym.auth.SessionSecurityPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Invalidates authenticated sessions once their absolute lifetime expires. */
class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    private final SessionSecurityPolicy sessionSecurityPolicy;

    AbsoluteSessionTimeoutFilter(SessionSecurityPolicy sessionSecurityPolicy) {
        this.sessionSecurityPolicy = sessionSecurityPolicy;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (isAuthenticated(authentication) && request.getSession(false) != null) {
            var session = request.getSession(false);
            if (sessionSecurityPolicy.isExpired(session)) {
                session.invalidate();
                SecurityContextHolder.clearContext();
            } else {
                sessionSecurityPolicy.ensureIssuedAt(session);
            }
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
