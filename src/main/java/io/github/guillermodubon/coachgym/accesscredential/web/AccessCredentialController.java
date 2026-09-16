package io.github.guillermodubon.coachgym.accesscredential.web;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDetails;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDocument;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialApplicationService;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialContent;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialHistoryPage;
import io.github.guillermodubon.coachgym.accesscredential.application.IssueAccessCredentialCommand;
import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Staff HTTP boundary for the durable client access-credential lifecycle. */
@RestController
@RequestMapping("/api/v1/clients/{clientId}/access-credential")
@Tag(name = "Access Credentials", description = "Staff access-credential lifecycle and PNG delivery.")
@SecurityRequirement(name = "sessionCookie")
class AccessCredentialController {

    private final AccessCredentialApplicationService service;

    AccessCredentialController(AccessCredentialApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Issue a client access credential",
            description = "Creates or returns the canonical active credential. All credential and token fields are server controlled. Requires CSRF.")
    @ApiResponse(responseCode = "201", description = "Credential issued or canonical active credential returned",
            content = @Content(schema = @Schema(implementation = AccessCredentialResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions or invalid CSRF token")
    @ApiResponse(responseCode = "404", description = "Client not found")
    @ApiResponse(responseCode = "409", description = "Client is not eligible for a credential")
    ResponseEntity<AccessCredentialResponse> issue(
            @PathVariable UUID clientId,
            @RequestBody(required = false) IssueAccessCredentialRequest request,
        Authentication authentication) {
        AccessCredentialDetails details = service.issue(
                new IssueAccessCredentialCommand(clientId),
                actor(authentication));
        return ResponseEntity.created(resourceUri(clientId))
                .body(response(details));
    }

    @GetMapping
    @Operation(summary = "Get the active access credential metadata")
    @ApiResponse(responseCode = "200", description = "Credential metadata returned",
            content = @Content(schema = @Schema(implementation = AccessCredentialResponse.class)))
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @ApiResponse(responseCode = "404", description = "Active credential not found")
    AccessCredentialResponse findActive(@PathVariable UUID clientId) {
        return response(service.findActiveByClientId(clientId));
    }

    @GetMapping(value = "/content", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(
            summary = "Download the active credential PNG",
            description = "Returns the persisted PNG with private no-store caching and content sniffing protection.")
    @ApiResponse(responseCode = "200", description = "PNG returned",
            content = @Content(mediaType = MediaType.IMAGE_PNG_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @ApiResponse(responseCode = "404", description = "Active credential not found")
    @ApiResponse(responseCode = "500", description = "Credential document unavailable")
    ResponseEntity<byte[]> download(@PathVariable UUID clientId) {
        AccessCredentialContent content = service.downloadActiveByClientId(clientId);
        AccessCredentialDocument document = content.document();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(document.sizeBytes())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename(content.details().credentialCode()) + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(document.bytes());
    }

    @PostMapping("/revoke")
    @Operation(
            summary = "Revoke the active access credential",
            description = "Irreversibly revokes the active credential with an operational reason and expected version. Requires CSRF.")
    @ApiResponse(responseCode = "200", description = "Credential revoked",
            content = @Content(schema = @Schema(implementation = AccessCredentialResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid reason or version")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions or invalid CSRF token")
    @ApiResponse(responseCode = "404", description = "Active credential not found")
    @ApiResponse(responseCode = "409", description = "Credential state or version conflict")
    AccessCredentialResponse revoke(
            @PathVariable UUID clientId,
            @Valid @RequestBody RevokeAccessCredentialRequest request,
            Authentication authentication) {
        return response(service.revoke(request.toCommand(clientId), actor(authentication)));
    }

    @PostMapping("/replace")
    @Operation(
            summary = "Replace the active access credential",
            description = "Atomically revokes the active credential and issues its replacement with an operational reason. Requires CSRF.")
    @ApiResponse(responseCode = "200", description = "Credential replaced",
            content = @Content(schema = @Schema(implementation = AccessCredentialResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid reason or version")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions or invalid CSRF token")
    @ApiResponse(responseCode = "404", description = "Active credential not found")
    @ApiResponse(responseCode = "409", description = "Credential state or version conflict")
    AccessCredentialResponse replace(
            @PathVariable UUID clientId,
            @Valid @RequestBody ReplaceAccessCredentialRequest request,
            Authentication authentication) {
        return response(service.replace(request.toCommand(clientId), actor(authentication)));
    }

    @GetMapping("/history")
    @Operation(summary = "List access-credential lifecycle history")
    @ApiResponse(responseCode = "200", description = "History page returned",
            content = @Content(schema = @Schema(implementation = AccessCredentialHistoryPageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid pagination")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    AccessCredentialHistoryPageResponse history(
            @PathVariable UUID clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        AccessCredentialHistoryPage result = service.findHistoryByClientId(clientId, page, size);
        return AccessCredentialHistoryPageResponse.from(result);
    }

    private static AccessCredentialResponse response(AccessCredentialDetails details) {
        return AccessCredentialResponse.from(details, downloadUri(details.clientId()));
    }

    private static URI resourceUri(UUID clientId) {
        return UriComponentsBuilder.newInstance()
                .path("/api/v1/clients/{clientId}/access-credential")
                .buildAndExpand(clientId)
                .toUri();
    }

    private static URI downloadUri(UUID clientId) {
        return UriComponentsBuilder.newInstance()
                .path("/api/v1/clients/{clientId}/access-credential/content")
                .buildAndExpand(clientId)
                .toUri();
    }

    private static String filename(String credentialCode) {
        String safe = credentialCode == null
                ? "credential"
                : credentialCode.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank()) {
            safe = "credential";
        }
        if (!safe.toLowerCase(Locale.ROOT).endsWith(".png")) {
            safe += ".png";
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
