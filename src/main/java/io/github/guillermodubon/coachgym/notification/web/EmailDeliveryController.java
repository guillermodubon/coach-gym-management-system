package io.github.guillermodubon.coachgym.notification.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryStatus;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryPage;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliverySearchQuery;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliverySortDirection;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliverySortField;
import io.github.guillermodubon.coachgym.notification.application.RequestAccessCredentialEmailCommand;
import io.github.guillermodubon.coachgym.notification.application.RequestPaymentReceiptEmailCommand;
import io.github.guillermodubon.coachgym.notification.application.TransactionalEmailDeliveryApplicationService;
import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValidationException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Secured staff boundary for canonical transactional email delivery operations. */
@RestController
@RequestMapping("/api/v1/email-deliveries")
@Tag(
        name = "Transactional Email Deliveries",
        description = "ADMIN and RECEPTIONIST only: provider-neutral transactional delivery requests, history, attempts, "
                + "and bounded retries. Mailpit is local/demo infrastructure only; external SMTP "
                + "delivery is not guaranteed exactly once and this API does not support bulk email.")
@SecurityRequirement(name = "sessionCookie")
class EmailDeliveryController {

    private final TransactionalEmailDeliveryApplicationService service;

    EmailDeliveryController(TransactionalEmailDeliveryApplicationService service) {
        this.service = service;
    }

    @PostMapping("/payment-receipts/{paymentId}")
    @Operation(
            summary = "Request a payment-receipt email",
            description = "ADMIN and RECEPTIONIST may load the authoritative client recipient and canonical PDF from the server. "
                    + "The request body is empty and the operation requires CSRF.")
    @ApiResponse(responseCode = "201", description = "Canonical delivery returned",
            content = @Content(schema = @Schema(implementation = EmailDeliveryResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid payment or request")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions or invalid CSRF token")
    @ApiResponse(responseCode = "404", description = "Payment or canonical receipt not found")
    @ApiResponse(responseCode = "422", description = "Authoritative recipient or attachment unavailable")
    @ApiResponse(responseCode = "409", description = "Delivery state or idempotency conflict")
    ResponseEntity<EmailDeliveryResponse> requestPaymentReceipt(
            @PathVariable UUID paymentId,
            @RequestBody(required = false) RequestEmailRequest request,
            Authentication authentication,
            UriComponentsBuilder uriBuilder) {
        EmailDeliveryResponse response = EmailDeliveryResponse.from(
                service.requestPaymentReceiptEmail(
                        new RequestPaymentReceiptEmailCommand(paymentId),
                        actor(authentication)));
        return created(response, uriBuilder);
    }

    @PostMapping("/access-credentials/{clientId}")
    @Operation(
            summary = "Request an access-credential email",
            description = "ADMIN and RECEPTIONIST may load the authoritative client recipient and canonical PNG from the server. "
                    + "The request body is empty and the operation requires CSRF.")
    @ApiResponse(responseCode = "201", description = "Canonical delivery returned",
            content = @Content(schema = @Schema(implementation = EmailDeliveryResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid client or request")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions or invalid CSRF token")
    @ApiResponse(responseCode = "404", description = "Client or canonical credential not found")
    @ApiResponse(responseCode = "422", description = "Authoritative recipient or attachment unavailable")
    @ApiResponse(responseCode = "409", description = "Delivery state or idempotency conflict")
    ResponseEntity<EmailDeliveryResponse> requestAccessCredential(
            @PathVariable UUID clientId,
            @RequestBody(required = false) RequestEmailRequest request,
            Authentication authentication,
            UriComponentsBuilder uriBuilder) {
        EmailDeliveryResponse response = EmailDeliveryResponse.from(
                service.requestAccessCredentialEmail(
                        new RequestAccessCredentialEmailCommand(clientId),
                        actor(authentication)));
        return created(response, uriBuilder);
    }

    @GetMapping
    @Operation(
            summary = "List transactional email delivery history",
            description = "ADMIN and RECEPTIONIST may read bounded, allowlisted operational history. "
                    + "Recipient addresses are masked.")
    @ApiResponse(responseCode = "200", description = "Delivery history returned",
            content = @Content(schema = @Schema(implementation = EmailDeliveryPageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid filter, pagination, or sort")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    EmailDeliveryPageResponse findAll(
            @RequestParam(required = false) String deliveryType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) UUID sourceResourceId,
            @RequestParam(required = false) String requestedFrom,
            @RequestParam(required = false) String requestedUntil,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "REQUESTED_AT") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            Authentication authentication) {
        EmailDeliveryPage result = service.findAll(new EmailDeliverySearchQuery(
                parseEnum(deliveryType, EmailDeliveryType.class, "delivery type"),
                parseEnum(status, EmailDeliveryStatus.class, "delivery status"),
                clientId,
                sourceResourceId,
                parseInstant(requestedFrom, "requestedFrom"),
                parseInstant(requestedUntil, "requestedUntil"),
                page,
                size,
                EmailDeliverySortField.from(sort),
                EmailDeliverySortDirection.from(direction)));
        return EmailDeliveryPageResponse.from(result);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a transactional email delivery",
            description = "ADMIN and RECEPTIONIST may read safe delivery metadata and the masked authoritative recipient.")
    @ApiResponse(responseCode = "200", description = "Delivery returned",
            content = @Content(schema = @Schema(implementation = EmailDeliveryResponse.class)))
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @ApiResponse(responseCode = "404", description = "Delivery not found")
    EmailDeliveryResponse findById(@PathVariable UUID id, Authentication authentication) {
        return EmailDeliveryResponse.from(service.findById(id));
    }

    @GetMapping("/{id}/attempts")
    @Operation(
            summary = "List delivery attempts",
            description = "ADMIN and RECEPTIONIST may read append-only attempt metadata without provider identifiers or failure text.")
    @ApiResponse(responseCode = "200", description = "Attempts returned")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @ApiResponse(responseCode = "404", description = "Delivery not found")
    java.util.List<EmailDeliveryAttemptResponse> findAttempts(
            @PathVariable UUID id,
            Authentication authentication) {
        return service.findAttempts(id).stream().map(EmailDeliveryAttemptResponse::from).toList();
    }

    @PostMapping("/{id}/retry")
    @Operation(
            summary = "Retry a failed email delivery",
            description = "ADMIN and RECEPTIONIST may retry only a persisted FAILED delivery using server-owned snapshots. "
                    + "The expected version and a valid CSRF token are required; retries are bounded.")
    @ApiResponse(responseCode = "200", description = "Delivery retry completed",
            content = @Content(schema = @Schema(implementation = EmailDeliveryResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid retry request")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions or invalid CSRF token")
    @ApiResponse(responseCode = "404", description = "Delivery not found")
    @ApiResponse(responseCode = "409", description = "State, version, or retry-limit conflict")
    @ApiResponse(responseCode = "422", description = "Canonical attachment unavailable")
    EmailDeliveryResponse retry(
            @PathVariable UUID id,
            @RequestBody RetryEmailDeliveryRequest request,
            Authentication authentication) {
        return EmailDeliveryResponse.from(service.retryEmailDelivery(
                request.toCommand(id), actor(authentication)));
    }

    private static ResponseEntity<EmailDeliveryResponse> created(
            EmailDeliveryResponse response,
            UriComponentsBuilder uriBuilder) {
        URI location = uriBuilder.path("/api/v1/email-deliveries/{id}")
                .buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof CoachGymUserPrincipal principal)) {
            throw new IllegalStateException("Authenticated staff principal is required.");
        }
        return principal.authenticatedActor();
    }

    private static Instant parseInstant(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.strip());
        } catch (DateTimeParseException exception) {
            throw new EmailDeliveryValidationException("Invalid " + field + " timestamp.");
        }
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> type, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new EmailDeliveryValidationException("Unsupported " + label + ".");
        }
    }
}
