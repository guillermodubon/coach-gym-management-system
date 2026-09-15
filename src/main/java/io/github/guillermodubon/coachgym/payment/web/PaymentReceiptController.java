package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDetails;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDocument;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptApplicationService;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptContent;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** HTTP boundary for staff receipt generation, metadata retrieval, and PDF download. */
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payment Receipts", description = "Canonical payment receipt metadata and PDF documents.")
@SecurityRequirement(name = "sessionCookie")
class PaymentReceiptController {

    private static final String DOWNLOAD_SUFFIX = "/receipt.pdf";

    private final PaymentReceiptApplicationService service;

    PaymentReceiptController(PaymentReceiptApplicationService service) {
        this.service = service;
    }

    @PostMapping("/{paymentId}/receipt")
    @Operation(
            summary = "Generate a canonical payment receipt",
            description = "Generates or returns the immutable receipt for a confirmed PAID payment. "
                    + "All payment and identity data is read from the server. A valid CSRF token is required.")
    @ApiResponse(responseCode = "201", description = "Receipt generated or canonical receipt returned",
            content = @Content(schema = @Schema(implementation = PaymentReceiptResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid payment identifier or request")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions or invalid CSRF token")
    @ApiResponse(responseCode = "404", description = "Payment not found")
    @ApiResponse(responseCode = "409", description = "Payment is not eligible for a receipt")
    @ApiResponse(responseCode = "500", description = "Receipt generation failed")
    ResponseEntity<PaymentReceiptResponse> generate(
            @PathVariable UUID paymentId,
            @RequestBody(required = false) GeneratePaymentReceiptRequest request,
            Authentication authentication) {

        GeneratePaymentReceiptRequest safeRequest = request == null
                ? new GeneratePaymentReceiptRequest()
                : request;
        PaymentReceiptDetails details = service.generate(
                safeRequest.toCommand(paymentId), actor(authentication));
        URI location = receiptUri(paymentId);
        return ResponseEntity.created(location)
                .body(response(details));
    }

    @GetMapping("/{paymentId}/receipt")
    @Operation(
            summary = "Get payment receipt metadata",
            description = "Returns the immutable metadata and financial snapshot of the canonical receipt.")
    @ApiResponse(responseCode = "200", description = "Receipt metadata returned",
            content = @Content(schema = @Schema(implementation = PaymentReceiptResponse.class)))
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @ApiResponse(responseCode = "404", description = "Receipt not found")
    PaymentReceiptResponse findByPaymentId(
            @PathVariable UUID paymentId) {
        return response(service.findByPaymentId(paymentId));
    }

    @GetMapping(value = "/{paymentId}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(
            summary = "Download the canonical payment receipt PDF",
            description = "Returns the persisted canonical PDF after validating its checksum, size, and content type.")
    @ApiResponse(responseCode = "200", description = "Receipt PDF returned",
            content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @ApiResponse(responseCode = "404", description = "Receipt not found")
    @ApiResponse(responseCode = "500", description = "Receipt document unavailable")
    ResponseEntity<byte[]> download(@PathVariable UUID paymentId) {
        PaymentReceiptContent content = service.downloadByPaymentId(paymentId);
        PaymentReceiptDocument document = content.document();
        String filename = filename(content.details().receiptNumber());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(document.sizeBytes())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(document.bytes());
    }

    private static PaymentReceiptResponse response(PaymentReceiptDetails details) {
        URI downloadUrl = receiptUri(details.paymentId(), DOWNLOAD_SUFFIX);
        return PaymentReceiptResponse.from(details, downloadUrl);
    }

    private static URI receiptUri(UUID paymentId) {
        return receiptUri(paymentId, "/receipt");
    }

    private static URI receiptUri(UUID paymentId, String suffix) {
        return UriComponentsBuilder.newInstance()
                .path("/api/v1/payments/{paymentId}" + suffix)
                .buildAndExpand(paymentId)
                .toUri();
    }

    private static String filename(String receiptNumber) {
        String safe = receiptNumber == null
                ? "receipt"
                : receiptNumber.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank()) {
            safe = "receipt";
        }
        if (!safe.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            safe += ".pdf";
        }
        return "coach-gym-" + safe;
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof CoachGymUserPrincipal principal)) {
            throw new IllegalStateException("Authenticated staff principal is required.");
        }
        return principal.authenticatedActor();
    }
}
