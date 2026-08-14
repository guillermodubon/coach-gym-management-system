package io.github.guillermodubon.coachgym.client.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.client.ClientDetails;
import io.github.guillermodubon.coachgym.client.application.ClientApplicationService;
import io.github.guillermodubon.coachgym.client.application.ClientNotFoundException;
import io.github.guillermodubon.coachgym.client.application.DuplicateClientException;
import io.github.guillermodubon.coachgym.client.domain.ClientValidationException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/clients")
@Tag(name = "Clients", description = "Client registration and lookup.")
class ClientController {

    private final ClientApplicationService clientApplicationService;

    ClientController(ClientApplicationService clientApplicationService) {
        this.clientApplicationService = clientApplicationService;
    }

    @PostMapping
    @Operation(summary = "Register a client")
    @ApiResponse(responseCode = "201", description = "Client created")
    @ApiResponse(responseCode = "409", description = "Client email already exists")
    ResponseEntity<ClientResponse> register(
            @Valid @RequestBody RegisterClientRequest request,
            Authentication authentication,
            UriComponentsBuilder uriBuilder) {
        ClientDetails client = clientApplicationService.register(request.toCommand(), actor(authentication));
        URI location = uriBuilder.path("/api/v1/clients/{id}").buildAndExpand(client.id()).toUri();
        return ResponseEntity.created(location).body(ClientResponse.from(client));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a client by id")
    @ApiResponse(responseCode = "200", description = "Client found")
    @ApiResponse(responseCode = "404", description = "Client not found")
    ClientResponse findById(@PathVariable UUID id) {
        return ClientResponse.from(clientApplicationService.findById(id));
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        CoachGymUserPrincipal principal = (CoachGymUserPrincipal) authentication.getPrincipal();
        return principal.authenticatedActor();
    }

    @ExceptionHandler(ClientValidationException.class)
    ResponseEntity<ProblemDetail> handleValidation(ClientValidationException exception) {
        return ResponseEntity.badRequest().body(ApiProblemFactory.create(
                HttpStatus.BAD_REQUEST, "CLIENT_VALIDATION_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(DuplicateClientException.class)
    ResponseEntity<ProblemDetail> handleDuplicate(DuplicateClientException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiProblemFactory.create(
                HttpStatus.CONFLICT, "DUPLICATE_CLIENT", exception.getMessage()));
    }

    @ExceptionHandler(ClientNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(ClientNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiProblemFactory.create(
                HttpStatus.NOT_FOUND, "CLIENT_NOT_FOUND", exception.getMessage()));
    }
}
