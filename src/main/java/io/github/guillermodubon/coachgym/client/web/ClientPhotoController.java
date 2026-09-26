package io.github.guillermodubon.coachgym.client.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.client.ClientPhotoDetails;
import io.github.guillermodubon.coachgym.client.application.ClientPhotoApplicationService;
import io.github.guillermodubon.coachgym.client.application.ClientPhotoContent;
import io.github.guillermodubon.coachgym.client.application.UploadClientPhotoCommand;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/clients/{clientId}/photo")
class ClientPhotoController {

    private final ClientPhotoApplicationService service;

    ClientPhotoController(ClientPhotoApplicationService service) {
        this.service = service;
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload or replace a client photo",
            description = "ADMIN and RECEPTIONIST can upload JPEG, PNG, or WebP content up to 5 MiB. Requires CSRF.",
            security = @SecurityRequirement(name = "sessionCookie"))
    ClientPhotoDetails upload(
            @PathVariable UUID clientId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws java.io.IOException {
        UploadClientPhotoCommand command = new UploadClientPhotoCommand(
                file.getContentType(), file.getBytes());
        return service.upload(clientId, command, actor(authentication));
    }

    @GetMapping
    @Operation(
            summary = "Download a client photo",
            security = @SecurityRequirement(name = "sessionCookie"))
    ResponseEntity<byte[]> load(
            @PathVariable UUID clientId,
            Authentication authentication) {
        ClientPhotoContent content = service.load(clientId, actor(authentication));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .eTag('"' + content.checksumSha256() + '"')
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(content.bytes());
    }

    @DeleteMapping
    @Operation(
            summary = "Delete a client photo",
            description = "ADMIN only. Requires CSRF and the current photo version.",
            security = @SecurityRequirement(name = "sessionCookie"))
    ResponseEntity<Void> delete(
            @PathVariable UUID clientId,
            @RequestParam long version,
            Authentication authentication) {
        service.delete(clientId, version, actor(authentication));
        return ResponseEntity.noContent().build();
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof CoachGymUserPrincipal principal)) {
            throw new IllegalStateException(
                    "Authenticated staff principal is required.");
        }
        return principal.authenticatedActor();
    }
}
