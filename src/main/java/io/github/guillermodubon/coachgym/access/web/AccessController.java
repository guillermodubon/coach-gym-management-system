package io.github.guillermodubon.coachgym.access.web;

import io.github.guillermodubon.coachgym.access.AccessRecordDetails;
import io.github.guillermodubon.coachgym.access.application.AccessApplicationService;
import io.github.guillermodubon.coachgym.access.application.AccessRecordNotFoundException;
import io.github.guillermodubon.coachgym.access.application.AccessRecordSearchQuery;
import io.github.guillermodubon.coachgym.access.domain.AccessValidationException;
import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Processes gym access decisions and exposes the append-only access history.
 */
@RestController
@RequestMapping("/api/v1/access")
@Tag(
        name = "Gym Access",
        description = """
                Gym access check-in decisions and access-record history

                A processed check-in returns HTTP 200 for both ALLOWED and
                DENIED business results. Consumers must inspect result and
                reasonCode to determine the decision.

                Only administrators and receptionists may process check-ins
                or query access-records.
                """)
public class AccessController {

    private final AccessApplicationService accessApplicationService;

    public AccessController(AccessApplicationService accessApplicationService) {
        this.accessApplicationService = accessApplicationService;
    }

    @PostMapping("/check-in")
    @Operation(
            summary = "Process a gym access check-in",
            description = """
                    Resolves a client or membership operational identifier,
                    evaluates the current access policy, persists the attempt,
                    and returns the resulting decision.

                    HTTP 200 may contain either ALLOWED or DENIED. A business
                    denial is a successfully processed decision and is not an
                    HTTP authorization error.

                    Every normal business attempt is persisted. Technical
                    validation, authentication, authorization, and CSRF
                    failures do not create access records.

                    This operation requires an authenticated ADMIN or
                    RECEPTIONIST session and a valid CSRF token.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "Check-in processed; result may be ALLOWED or DENIED")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid or malformed request")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description = "Insufficient role or invalid CSRF token")
    public ResponseEntity<AccessRecordResponse> checkIn(
            @Valid @RequestBody CheckInRequest request,
            Authentication authentication) {

        AccessRecordDetails record = accessApplicationService.checkIn(
                request.toCommand(),
                actor(authentication));

        return ResponseEntity.ok(AccessRecordResponse.from(record));
    }

    @GetMapping("/records/{id}")
    @Operation(
            summary = "Retrieve one access record",
            description = """
                    Retrieves an append-only access attempt by its identifier.

                    This operation requires an authenticated ADMIN or
                    RECEPTIONIST session.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "Access record returned")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description = "Insufficient permissions")
    @ApiResponse(
            responseCode = "404",
            description = "Access record not found")
    public ResponseEntity<AccessRecordResponse> findById(@PathVariable UUID id) {
        AccessRecordDetails record = accessApplicationService.findById(id);
        return ResponseEntity.ok(AccessRecordResponse.from(record));
    }

    @GetMapping("/records")
    @Operation(
            summary = "List access records",
            description = """
                    Returns the append-only access-record history using
                    optional allowlisted filters, zero-based pagination,
                    and stable sorting.

                    Supported filters:
                    clientId, membershipId, result, reasonCode,
                    checkedInFrom, checkedInUntil, and processedByUserId.

                    The only supported sort field is CHECKED_IN_AT.
                    Supported directions are ASC and DESC.
                    The default order is CHECKED_IN_AT DESC, followed by
                    record ID ascending for stable pagination.

                    This operation requires an authenticated ADMIN or
                    RECEPTIONIST session.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "Paginated access history returned")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid filter, pagination, sort, or direction")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description = "Insufficient permissions")
    public ResponseEntity<AccessRecordPageResponse> findAll(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) UUID membershipId,
            @Parameter(description = "ALLOWED or DENIED")
            @RequestParam(required = false) String result,
            @Parameter(description = """
                    ACCESS_ALLOWED, IDENTIFIER_NOT_FOUND,
                    CLIENT_INACTIVE, MEMBERSHIP_NOT_FOUND,
                    MEMBERSHIP_NOT_STARTED,
                    MEMBERSHIP_PERIOD_EXPIRED,
                    MEMBERSHIP_FROZEN, MEMBERSHIP_EXPIRED,
                    or MEMBERSHIP_CANCELLED
                    """)
            @RequestParam(required = false) String reasonCode,
            @RequestParam(required = false) Instant checkedInFrom,
            @RequestParam(required = false) Instant checkedInUntil,
            @RequestParam(required = false) UUID processedByUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @Parameter(description = "Only CHECKED_IN_AT is supported")
            @RequestParam(defaultValue = "CHECKED_IN_AT") String sort,
            @Parameter(description = "ASC or DESC")
            @RequestParam(defaultValue = "DESC") String direction) {

        AccessRecordSearchQuery query = AccessRecordSearchQuery.from(
                clientId,
                membershipId,
                result,
                reasonCode,
                checkedInFrom,
                checkedInUntil,
                processedByUserId,
                page,
                size,
                sort,
                direction);

        return ResponseEntity.ok(AccessRecordPageResponse.from(accessApplicationService.findAll(query)));
    }

    @ExceptionHandler(AccessValidationException.class)
    ResponseEntity<ProblemDetail> handleValidation(
            AccessValidationException exception) {

        return problem(
                HttpStatus.BAD_REQUEST,
                "ACCESS_VALIDATION_FAILED",
                exception.getMessage());
    }

    @ExceptionHandler(AccessRecordNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(
            AccessRecordNotFoundException exception) {

        return problem(
                HttpStatus.NOT_FOUND,
                "ACCESS_RECORD_NOT_FOUND",
                exception.getMessage());
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String detail) {

        return ResponseEntity
                .status(status)
                .body(
                        ApiProblemFactory.create(
                                status,
                                code,
                                detail));
    }

    private AuthenticatedActor actor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CoachGymUserPrincipal principal) {
            return principal.authenticatedActor();
        }
        throw new IllegalStateException("Authentication principal is missing or invalid.");
    }
}