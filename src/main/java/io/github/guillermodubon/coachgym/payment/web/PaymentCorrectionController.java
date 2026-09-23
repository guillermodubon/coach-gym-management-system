package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.payment.PaymentCorrectionDetails;
import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionApplicationService;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(
        name = "Payment Corrections",
        description = "Administrative payment voids, full refunds, and history.")
@SecurityRequirement(name = "sessionCookie")
class PaymentCorrectionController {

    private final PaymentCorrectionApplicationService service;

    PaymentCorrectionController(
            PaymentCorrectionApplicationService service) {
        this.service = service;
    }

    @PostMapping("/{paymentId}/void")
    @Operation(
            summary = "Void a payment",
            description = """
                    Voids an incorrectly registered PAID payment.

                    Only administrators can execute this operation. The reason
                    and current optimistic version are required. The payment is
                    preserved and no refund record is created. CSRF is required.
                    """)
    @ApiResponse(responseCode = "200", description = "Payment voided")
    @ApiResponse(responseCode = "400", description = "Invalid correction request")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Administrator role and valid CSRF token required")
    @ApiResponse(responseCode = "404", description = "Payment not found")
    @ApiResponse(responseCode = "409", description = "Version or payment state conflict")
    PaymentCorrectionResponse voidPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody VoidPaymentRequest request,
            Authentication authentication) {
        return PaymentCorrectionResponse.from(
                service.voidPayment(
                        request.toCommand(paymentId),
                        actor(authentication)));
    }

    @PostMapping("/{paymentId}/refund")
    @Operation(
            summary = "Record a full payment refund",
            description = """
                    Records a full refund for a PAID payment.

                    Only administrators can execute this operation. Amount,
                    currency, payment method, actor, and timestamp are obtained
                    by the server from the original payment and authenticated
                    session. Partial refunds are not supported. CSRF is required.
                    """)
    @ApiResponse(responseCode = "200", description = "Full refund recorded")
    @ApiResponse(responseCode = "400", description = "Invalid refund request")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Administrator role and valid CSRF token required")
    @ApiResponse(responseCode = "404", description = "Payment not found")
    @ApiResponse(responseCode = "409", description = "Version, state, or refund conflict")
    PaymentCorrectionResponse refundPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody RefundPaymentRequest request,
            Authentication authentication) {
        return PaymentCorrectionResponse.from(
                service.refundPayment(
                        request.toCommand(paymentId),
                        actor(authentication)));
    }

    @GetMapping("/{paymentId}/correction")
    @Operation(
            summary = "Get a payment correction",
            description = """
                    Returns the current void or refund associated with a payment.
                    Administrators and receptionists can execute this operation.
                    """)
    @ApiResponse(responseCode = "200", description = "Payment correction returned")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @ApiResponse(responseCode = "404", description = "Payment correction not found")
    PaymentCorrectionResponse findCorrection(
            @PathVariable UUID paymentId,
            Authentication authentication) {
        PaymentCorrectionDetails correction = service.findCorrection(
                        paymentId, actor(authentication))
                .orElseThrow(() ->
                        new PaymentCorrectionResourceNotFoundException(paymentId));
        return PaymentCorrectionResponse.from(correction);
    }

    @GetMapping("/{paymentId}/status-history")
    @Operation(
            summary = "List payment correction history",
            description = """
                    Returns append-only correction transitions newest first.
                    The initial payment registration is excluded. Page is
                    zero-based and size must be between 1 and 100.
                    Administrators and receptionists can execute this operation.
                    """)
    @ApiResponse(responseCode = "200", description = "Payment history returned")
    @ApiResponse(responseCode = "400", description = "Invalid pagination")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    PaymentStatusHistoryPageResponse findStatusHistory(
            @PathVariable UUID paymentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication authentication) {
        return PaymentStatusHistoryPageResponse.from(
                service.findStatusHistory(paymentId, page, size, actor(authentication)));
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        CoachGymUserPrincipal principal =
                (CoachGymUserPrincipal) authentication.getPrincipal();
        return principal.authenticatedActor();
    }
}
