package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.application.StaffPasswordRecoveryApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, non-enumerating password recovery endpoints for existing staff only. */
@RestController
@Tag(name = "Staff password recovery", description =
        "Provider-neutral, non-enumerating recovery for existing staff accounts; public registration is unavailable. "
                + "Recovery states are PENDING, USED, EXPIRED, and REVOKED. Links expire after 15 minutes by default "
                + "and never exceed 30 minutes.")
class PasswordRecoveryController {

    private final StaffPasswordRecoveryApplicationService recovery;

    PasswordRecoveryController(StaffPasswordRecoveryApplicationService recovery) {
        this.recovery = recovery;
    }

    @PostMapping("/api/v1/auth/password-recovery-requests")
    @Operation(summary = "Request password recovery", description =
            "Always returns the same acknowledgement for eligible, ineligible, unknown, inactive, or throttled addresses. "
                    + "The one-time link is sent through the configured provider-neutral identity-email boundary. "
                    + "Abuse controls do not alter the public response. Requires CSRF.")
    @ApiResponse(responseCode = "202", description = "Generic acknowledgement")
    ResponseEntity<PasswordRecoveryAcknowledgement> request(
            @Valid @RequestBody RequestPasswordRecoveryRequest request,
            HttpServletRequest servletRequest) {
        String message = recovery.requestRecovery(
                request.toCommand(), servletRequest.getRemoteAddr());
        return noStore(ResponseEntity.accepted().body(new PasswordRecoveryAcknowledgement(message)));
    }

    @PostMapping("/api/v1/auth/password-recovery/complete")
    @Operation(summary = "Complete password recovery", description =
            "Consumes an unexpired, one-time recovery link and replaces the password. New passwords must be 12–256 "
                    + "characters. Invalid, expired, revoked, unavailable, or abuse-limited attempts share one generic "
                    + "conflict response. Requires CSRF.")
    @ApiResponse(responseCode = "204", description = "Password updated")
    @ApiResponse(responseCode = "409", description = "Recovery request is not available")
    ResponseEntity<Void> complete(
            @Valid @RequestBody CompletePasswordRecoveryRequest request,
            HttpServletRequest servletRequest) {
        recovery.completeRecovery(request.toCommand(), servletRequest.getRemoteAddr());
        return noStore(ResponseEntity.noContent().build());
    }

    private static <T> ResponseEntity<T> noStore(ResponseEntity<T> response) {
        return ResponseEntity.status(response.getStatusCode())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("Referrer-Policy", "no-referrer")
                .body(response.getBody());
    }
}
