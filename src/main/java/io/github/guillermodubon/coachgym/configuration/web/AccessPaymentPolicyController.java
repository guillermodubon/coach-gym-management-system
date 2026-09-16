package io.github.guillermodubon.coachgym.configuration.web;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyActor;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyActorProvider;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyDetails;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyValidationException;
import io.github.guillermodubon.coachgym.configuration.application.AccessPaymentPolicyApplicationService;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administrative HTTP boundary for the global access-payment policy. */
@RestController
@RequestMapping("/api/v1/settings/access-payment-policy")
@Tag(
        name = "Settings",
        description = "Administrative settings that control gym access policy.")
@SecurityRequirement(name = "sessionCookie")
class AccessPaymentPolicyController {

    private final AccessPaymentPolicyApplicationService service;

    AccessPaymentPolicyController(AccessPaymentPolicyApplicationService service) {
        this.service = Objects.requireNonNull(service);
    }

    @GetMapping
    @Operation(
            summary = "Get the access-payment policy",
            description = "Returns the global policy used by manual and QR check-ins. "
                    + "Only ADMIN sessions may read it. When enabled, only confirmed PAID "
                    + "payments qualify for access; PaymentAttempt records and provider browser "
                    + "redirects never qualify. The default is disabled for backward-compatible "
                    + "operation, and this endpoint does not collect payments automatically.")
    @ApiResponse(
            responseCode = "200",
            description = "Current policy returned",
            content = @Content(schema = @Schema(implementation = AccessPaymentPolicyResponse.class)))
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Only administrators may read the policy")
    @ApiResponse(responseCode = "500", description = "Policy data is temporarily unavailable")
    AccessPaymentPolicyResponse findCurrent() {
        return response(service.findCurrent());
    }

    @PutMapping
    @Operation(
            summary = "Update the access-payment policy",
            description = "Updates the global manual and QR access requirement using the expected "
                    + "optimistic-lock version. Only ADMIN sessions may update it. A valid CSRF "
                    + "token is required. The server controls actor and timestamp values. "
                    + "When enabled, only confirmed PAID payments qualify; PaymentAttempt records "
                    + "and provider browser redirects are explicitly excluded, and no automatic "
                    + "payment collection is started.")
    @ApiResponse(
            responseCode = "200",
            description = "Policy updated",
            content = @Content(schema = @Schema(implementation = AccessPaymentPolicyResponse.class)))
    @ApiResponse(responseCode = "400", description = "Request validation failed")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Only administrators or a valid CSRF token are accepted")
    @ApiResponse(responseCode = "409", description = "Expected policy version is stale")
    @ApiResponse(responseCode = "500", description = "Policy data is temporarily unavailable")
    AccessPaymentPolicyResponse update(
            @Valid @RequestBody UpdateAccessPaymentPolicyRequest request,
            Authentication authentication) {
        if (request == null) {
            throw new AccessPaymentPolicyValidationException("Policy request is required.");
        }
        AccessPaymentPolicyDetails details = service.update(
                request.toCommand(),
                actor(authentication));
        return response(details);
    }

    private static AccessPaymentPolicyResponse response(AccessPaymentPolicyDetails details) {
        return AccessPaymentPolicyResponse.from(Objects.requireNonNull(details));
    }

    private static AccessPaymentPolicyActor actor(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof AccessPaymentPolicyActorProvider provider) {
            return provider.accessPaymentPolicyActor();
        }
        throw new AccessPaymentPolicyValidationException("Authenticated actor is required.");
    }

    @ExceptionHandler(AccessPaymentPolicyValidationException.class)
    ResponseEntity<ProblemDetail> handleValidation(AccessPaymentPolicyValidationException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "ACCESS_PAYMENT_POLICY_VALIDATION_FAILED",
                "The access-payment policy request is invalid.");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String detail) {
        return ResponseEntity.status(status).body(ApiProblemFactory.create(status, code, detail));
    }
}
