package io.github.guillermodubon.coachgym.configuration.web;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyActor;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyActorProvider;
import io.github.guillermodubon.coachgym.configuration.BranchAccessPaymentPolicyMode;
import io.github.guillermodubon.coachgym.configuration.UpdateBranchAccessPolicyCommand;
import io.github.guillermodubon.coachgym.configuration.application.BranchAccessPolicyApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyAuthorizationException;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyValidationException;
import io.github.guillermodubon.coachgym.configuration.application.BranchAccessPolicyDataAccessException;
import io.github.guillermodubon.coachgym.configuration.application.BranchAccessPolicyNotFoundException;
import io.github.guillermodubon.coachgym.configuration.application.BranchAccessPolicyVersionConflictException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;

/** HTTP contract for safe branch-level access-payment policy administration. */
@RestController
@RequestMapping("/api/v1/branches/{branchId}/access-payment-policy")
@Tag(name = "Branch Policies", description = "Branch-aware confirmed-payment access policy.")
@SecurityRequirement(name = "sessionCookie")
class BranchAccessPolicyController {

    private final BranchAccessPolicyApplicationService service;

    BranchAccessPolicyController(BranchAccessPolicyApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Read effective access-payment policy for a branch",
            description = "Returns only whether a confirmed PAID payment is required and the current version. Organization and assigned branch administrators may read an authorized active branch; an assigned receptionist receives this read-only operational value. A path branch ID is a resource locator, never authority.")
    @ApiResponse(responseCode = "200", description = "Effective branch policy returned")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Staff member is not authorized for this active branch")
    @ApiResponse(responseCode = "404", description = "Policy data unavailable")
    BranchAccessPolicyResponse findEffective(
            @PathVariable UUID branchId,
            Authentication authentication) {
        return BranchAccessPolicyResponse.from(
                service.findEffective(branchId, actor(authentication)));
    }

    @PutMapping
    @Operation(
            summary = "Set a branch access-payment override",
            description = "Sets REQUIRED or NOT_REQUIRED, or uses INHERIT to clear an existing override. The request includes the expected version. Only an active organization-scoped ADMIN may mutate this policy; CSRF is required. The effective policy affects access only and does not disclose payment records.")
    @ApiResponse(responseCode = "200", description = "Branch override updated or unchanged")
    @ApiResponse(responseCode = "400", description = "Invalid mode or expected version")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Organization ADMIN scope or valid CSRF token required")
    @ApiResponse(responseCode = "404", description = "Active branch policy unavailable")
    @ApiResponse(responseCode = "409", description = "Branch policy version conflict")
    BranchAccessPolicyResponse updateOverride(
            @PathVariable UUID branchId,
            @Valid @RequestBody UpdateBranchAccessPolicyRequest request,
            Authentication authentication) {
        return BranchAccessPolicyResponse.from(
                service.updateOverride(
                        request.toCommand(branchId),
                        actor(authentication)));
    }

    @DeleteMapping
    @Operation(
            summary = "Clear a branch access-payment override",
            description = "Clears the override by setting the branch mode to INHERIT, preserving the versioned row and history. Supply the current expectedVersion as a query parameter. Only an active organization-scoped ADMIN may perform this CSRF-protected mutation.")
    @ApiResponse(responseCode = "200", description = "Branch now inherits the organization default")
    @ApiResponse(responseCode = "400", description = "Missing or invalid expected version")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Organization ADMIN scope or valid CSRF token required")
    @ApiResponse(responseCode = "404", description = "Active branch policy unavailable")
    @ApiResponse(responseCode = "409", description = "Branch policy version conflict")
    BranchAccessPolicyResponse clearOverride(
            @PathVariable UUID branchId,
            @Parameter(description = "Current optimistic-lock version.", required = true, example = "2")
            @RequestParam @Min(0) long expectedVersion,
            Authentication authentication) {
        return BranchAccessPolicyResponse.from(
                service.updateOverride(
                        new UpdateBranchAccessPolicyCommand(
                                branchId,
                                BranchAccessPaymentPolicyMode.INHERIT,
                                expectedVersion),
                        actor(authentication)));
    }

    private static AccessPaymentPolicyActor actor(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof AccessPaymentPolicyActorProvider provider) {
            return provider.accessPaymentPolicyActor();
        }
        throw new AccessPaymentPolicyValidationException(
                "Authenticated actor is required.");
    }

    @ExceptionHandler(AccessPaymentPolicyValidationException.class)
    ResponseEntity<ProblemDetail> handleValidation(
            AccessPaymentPolicyValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST,
                "BRANCH_ACCESS_POLICY_VALIDATION_FAILED",
                "The branch access-payment policy request is invalid.");
    }

    @ExceptionHandler(AccessPaymentPolicyAuthorizationException.class)
    ResponseEntity<ProblemDetail> handleAuthorization(
            AccessPaymentPolicyAuthorizationException exception) {
        return problem(HttpStatus.FORBIDDEN,
                "BRANCH_ACCESS_POLICY_FORBIDDEN",
                "The authenticated staff member is not authorized for this branch policy operation.");
    }

    @ExceptionHandler(BranchAccessPolicyNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(
            BranchAccessPolicyNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND,
                "BRANCH_ACCESS_POLICY_NOT_FOUND",
                "The active branch access-payment policy is unavailable.");
    }

    @ExceptionHandler(BranchAccessPolicyVersionConflictException.class)
    ResponseEntity<ProblemDetail> handleConflict(
            BranchAccessPolicyVersionConflictException exception) {
        return problem(HttpStatus.CONFLICT,
                "BRANCH_ACCESS_POLICY_VERSION_CONFLICT",
                "The branch access-payment policy was modified by another operation.");
    }

    @ExceptionHandler(BranchAccessPolicyDataAccessException.class)
    ResponseEntity<ProblemDetail> handleDataAccessFailure(
            BranchAccessPolicyDataAccessException exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "BRANCH_ACCESS_POLICY_DATA_ACCESS_FAILED",
                "The branch access-payment policy operation could not be completed.");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String detail) {
        return ResponseEntity.status(status)
                .body(ApiProblemFactory.create(status, code, detail));
    }
}
