package io.github.guillermodubon.coachgym.reporting.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.reporting.application.DashboardApplicationService;
import io.github.guillermodubon.coachgym.reporting.application.DashboardQuery;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reporting")
@Tag(
        name = "Operational Reporting",
        description = "Role-aware operational dashboard metrics.")
class DashboardController {

    private final DashboardApplicationService service;

    DashboardController(
            DashboardApplicationService service) {

        this.service = service;
    }

    @GetMapping("/dashboard")
    @Operation(
            summary = "Get the operational dashboard",
            description =
                    "ADMIN receives complete operational and financial metrics. "
                            + "RECEPTIONIST receives memberships, today's access "
                            + "metrics, and personal unread notifications only. "
                            + "The endpoint is read-only and does not require CSRF.",
            security = @SecurityRequirement(
                    name = "sessionCookie"))
    OperationalDashboardResponse dashboard(
            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE)
            LocalDate until,

            Authentication authentication) {

        DashboardQuery query =
                new DashboardQuery(
                        from,
                        until);

        return OperationalDashboardResponse.from(
                service.getDashboard(
                        query,
                        actor(authentication)));
    }

    private static AuthenticatedActor actor(
            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof CoachGymUserPrincipal principal)) {

            throw new IllegalStateException(
                    "Authenticated staff principal is required.");
        }

        return principal.authenticatedActor();
    }
}