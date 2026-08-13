package io.github.guillermodubon.coachgym.auth.web;

import io.github.guillermodubon.coachgym.auth.application.AuthenticationService;
import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Comparator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Internal staff authentication and server-side sessions.")
class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    AuthenticationController(
            AuthenticationService authenticationService,
            SecurityContextRepository securityContextRepository) {
        this.authenticationService = authenticationService;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/csrf")
    @Operation(summary = "Get a CSRF token for state-changing requests")
    CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getToken(), csrfToken.getHeaderName(), csrfToken.getParameterName());
    }

    @PostMapping("/login")
    @Operation(summary = "Create an authenticated server-side session")
    @ApiResponse(responseCode = "204", description = "Authentication succeeded")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    ResponseEntity<Void> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        Authentication authentication = authenticationService.authenticate(request.identifier(), request.password());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current authenticated staff account")
    CurrentUserResponse currentUser(Authentication authentication) {
        CoachGymUserPrincipal principal = (CoachGymUserPrincipal) authentication.getPrincipal();
        return new CurrentUserResponse(
                principal.id(),
                principal.getUsername(),
                principal.fullName(),
                principal.getAuthorities().stream()
                        .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                        .sorted(Comparator.naturalOrder())
                        .toList());
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate the current server-side session")
    @ApiResponse(responseCode = "204", description = "Session invalidated")
    ResponseEntity<Void> logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        logoutHandler.logout(request, response, authentication);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
