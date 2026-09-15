package io.github.guillermodubon.coachgym.equipment.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryDetails;
import io.github.guillermodubon.coachgym.equipment.application.EquipmentCategoryApplicationService;
import io.github.guillermodubon.coachgym.equipment.application.EquipmentCategorySearchQuery;
import io.github.guillermodubon.coachgym.equipment.application.exception.DuplicateEquipmentCategoryException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentCategoryNotFoundException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentCategoryVersionConflictException;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentValidationException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/equipment-categories")
@Tag(
        name = "Equipment Categories",
        description = """
                Equipment category catalog management.

                Categories are never physically deleted. Administrators can
                activate or deactivate categories through explicit lifecycle
                operations.
                """)
@SecurityRequirement(name = "sessionCookie")
class EquipmentCategoryController {

    private final EquipmentCategoryApplicationService categoryService;

    EquipmentCategoryController(
            EquipmentCategoryApplicationService categoryService) {

        this.categoryService = categoryService;
    }


    @PostMapping
    @Operation(
            summary = "Create an equipment category",
            description = """
                    Creates a new equipment category.

                    Required role: ADMIN.
                    A valid CSRF token is required.

                    The category identifier, active state, timestamps and
                    version are controlled by the server and are not part of
                    the request contract.
                    """)
    @ApiResponse(
            responseCode = "201",
            description = "Category created")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid category request")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description = "ADMIN role or valid CSRF token required")
    @ApiResponse(
            responseCode = "409",
            description = "An equipment category with the same name exists")
    ResponseEntity<EquipmentCategoryResponse> create(
            @Valid
            @RequestBody
            EquipmentCategoryRequest request,
            Authentication authentication,
            UriComponentsBuilder uriBuilder) {

        EquipmentCategoryDetails created =
                categoryService.create(
                        request.toCreateCommand(),
                        actor(authentication));

        URI location =
                uriBuilder
                        .path(
                                "/api/v1/equipment-categories/{id}")
                        .buildAndExpand(
                                created.id())
                        .toUri();

        return ResponseEntity
                .created(location)
                .body(
                        EquipmentCategoryResponse.from(
                                created));
    }


    @GetMapping("/{id}")
    @Operation(
            summary = "Get an equipment category by ID",
            description = """
                    Returns an equipment category by its identifier.

                    Permitted roles: ADMIN and RECEPTIONIST.
                    This is a read-only operation and does not require a CSRF
                    token.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "Category returned")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description = "Insufficient permissions")
    @ApiResponse(
            responseCode = "404",
            description = "Category not found")
    EquipmentCategoryResponse findById(
            @Parameter(
                    description = "Equipment category UUID",
                    required = true)
            @PathVariable
            UUID id) {

        return EquipmentCategoryResponse.from(
                categoryService.findById(id));
    }

    @GetMapping
    @Operation(
            summary = "List equipment categories",
            description = """
                    Returns a paginated equipment-category catalog.

                    Permitted roles: ADMIN and RECEPTIONIST.

                    Supported filter:
                    active, with values true or false.

                    Supported sort fields:
                    name and id.

                    The default ordering is name ASC. The category identifier
                    is appended as a stable secondary sort where necessary.

                    Pagination is zero based. The default page size is 25 and
                    the maximum page size is 100.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "Categories returned")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid filter, pagination or sort value")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",             description = "Insufficient permissions")
    EquipmentCategoryPageResponse findAll(

            @Parameter(
                    description = """
                            Optional active-state filter. Supported values:
                            true and false.
                            """,
                    example = "true")
            @RequestParam(required = false)
            String active,

            @Parameter(
                    description = "Zero-based page index.",
                    example = "0")
            @RequestParam(defaultValue = "0")
            int page,

            @Parameter(
                    description = "Page size between 1 and 100.",
                    example = "25")
            @RequestParam(defaultValue = "25")
            int size,

            @Parameter(
                    description = "Sort field: name or id.",
                    example = "name")
            @RequestParam(defaultValue = "name")
            String sort,

            @Parameter(
                    description = "Sort direction: asc or desc.",
                    example = "asc")
            @RequestParam(defaultValue = "asc")
            String direction) {

        EquipmentCategorySearchQuery query =
                EquipmentCategorySearchQuery.from(
                        active,
                        page,
                        size,
                        sort,
                        direction);

        return EquipmentCategoryPageResponse.from(
                categoryService.findAll(query));
    }


    @PutMapping("/{id}")
    @Operation(
            summary = "Update an equipment category",
            description = """
                    Updates the mutable name and description fields of an
                    equipment category.

                    Required role: ADMIN.
                    A valid CSRF token is required.

                    The version property is mandatory and implements
                    optimistic locking. A stale version produces HTTP 409 with
                    code EQUIPMENT_CATEGORY_VERSION_CONFLICT.

                    The identifier, active state and timestamps are controlled
                    by the server and are not part of the request contract.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "Category updated")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid category request or missing version")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description = "ADMIN role or valid CSRF token required")
    @ApiResponse(
            responseCode = "404",
            description = "Category not found")
    @ApiResponse(
            responseCode = "409",             description = """
                    Duplicate category name or optimistic-lock version
                    conflict
                    """)
    EquipmentCategoryResponse update(
            @Parameter(
                    description = "Equipment category UUID",
                    required = true)
            @PathVariable
            UUID id,

            @Valid
            @RequestBody
            UpdateEquipmentCategoryRequest request,

            Authentication authentication) {

        return EquipmentCategoryResponse.from(
                categoryService.update(
                        request.toCommand(id),
                        actor(authentication)));
    }

    @PostMapping("/{id}/activate")
    @Operation(
            summary = "Activate an equipment category",
            description = """
                    Marks an equipment category as active.

                    Required role: ADMIN.
                    A valid CSRF token is required.

                    The version property is mandatory and implements
                    optimistic locking. A stale version produces HTTP 409.

                    Active categories can be assigned during equipment
                    registration and administrative equipment updates.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "Category activated")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request or missing version")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description = "ADMIN role or valid CSRF token required")
    @ApiResponse(
            responseCode = "404",
            description = "Category not found")
    @ApiResponse(
            responseCode = "409",
            description = "Optimistic-lock version conflict")
    EquipmentCategoryResponse activate(
            @Parameter(
                    description = "Equipment category UUID",
                    required = true)
            @PathVariable
            UUID id,

            @Valid
            @RequestBody
            CategoryLifecycleRequest request,

            Authentication authentication) {

        return EquipmentCategoryResponse.from(
                categoryService.activate(
                        request.toActivateCommand(id),
                        actor(authentication)));
    }

    @PostMapping("/{id}/deactivate")
    @Operation(
            summary = "Deactivate an equipment category",
            description = """
                    Marks an equipment category as inactive.

                    Required role: ADMIN.
                    A valid CSRF token is required.

                    The version property is mandatory and implements
                    optimistic locking. A stale version produces HTTP 409.

                    An inactive category remains available for historical
                    reads but cannot be assigned during new equipment
                    registration or administrative equipment updates.

                    Categories are not physically deleted.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "Category deactivated")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request or missing version")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description = "ADMIN role or valid CSRF token required")
    @ApiResponse(
            responseCode = "404",
            description = "Category not found")
    @ApiResponse(
            responseCode = "409",
            description = "Optimistic-lock version conflict")
    EquipmentCategoryResponse deactivate(
            @Parameter(
                    description = "Equipment category UUID",
                    required = true)
            @PathVariable
            UUID id,

            @Valid
            @RequestBody
            CategoryLifecycleRequest request,

            Authentication authentication) {

        return EquipmentCategoryResponse.from(
                categoryService.deactivate(
                        request.toDeactivateCommand(id),
                        actor(authentication)));
    }


    @ExceptionHandler(EquipmentValidationException.class)
    ResponseEntity<ProblemDetail> handleValidation(
            EquipmentValidationException exception) {

        return ResponseEntity
                .badRequest()
                .body(
                        ApiProblemFactory.create(
                                HttpStatus.BAD_REQUEST,
                                "EQUIPMENT_VALIDATION_FAILED",
                                "The equipment category request is invalid."));
    }

    @ExceptionHandler(EquipmentCategoryNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(
            EquipmentCategoryNotFoundException exception) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        ApiProblemFactory.create(
                                HttpStatus.NOT_FOUND,
                                "EQUIPMENT_CATEGORY_NOT_FOUND",
                                "The requested equipment category was not found."));
    }

    @ExceptionHandler(DuplicateEquipmentCategoryException.class)
    ResponseEntity<ProblemDetail> handleDuplicate(
            DuplicateEquipmentCategoryException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        ApiProblemFactory.create(
                                HttpStatus.CONFLICT,
                                "DUPLICATE_EQUIPMENT_CATEGORY",
                                "An equipment category with that name already exists."));
    }

    @ExceptionHandler(EquipmentCategoryVersionConflictException.class)
    ResponseEntity<ProblemDetail> handleVersionConflict(
            EquipmentCategoryVersionConflictException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        ApiProblemFactory.create(
                                HttpStatus.CONFLICT,
                                "EQUIPMENT_CATEGORY_VERSION_CONFLICT",
                                "The equipment category was modified by another request."));
    }

    // Authentication
    private static AuthenticatedActor actor(
            Authentication authentication) {

        CoachGymUserPrincipal principal =
                (CoachGymUserPrincipal)
                        authentication.getPrincipal();

        return principal.authenticatedActor();
    }
}

