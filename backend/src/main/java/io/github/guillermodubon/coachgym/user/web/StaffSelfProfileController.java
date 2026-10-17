package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.AuthenticatedActorProvider;
import io.github.guillermodubon.coachgym.user.StaffProfilePhotoDetails;
import io.github.guillermodubon.coachgym.user.StaffSelfProfileDetails;
import io.github.guillermodubon.coachgym.user.application.StaffPasswordChangeResult;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoContent;
import io.github.guillermodubon.coachgym.user.application.StaffSelfProfileApplicationService;
import io.github.guillermodubon.coachgym.user.application.UploadStaffProfilePhotoCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** HTTP boundary for authenticated staff self-profile operations. */
@RestController
@RequestMapping("/api/v1/me/profile")
@Tag(name = "Staff self-profile", description =
        "Authenticated staff profile, private photo, and password operations.")
@SecurityRequirement(name = "sessionCookie")
class StaffSelfProfileController {

    private final StaffSelfProfileApplicationService service;

    StaffSelfProfileController(StaffSelfProfileApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Get the authenticated staff self-profile",
            description = "Returns only the authenticated ADMIN or RECEPTIONIST profile. "
                    + "Role, status, organization, branch, and permission fields are not editable here.")
    @ApiResponse(responseCode = "200", description = "Profile returned",
            content = @Content(schema = @Schema(implementation = StaffSelfProfileResponse.class)))
    @ApiResponse(responseCode = "401", description = "Authentication required")
    StaffSelfProfileResponse findProfile(Authentication authentication) {
        return StaffSelfProfileResponse.from(
                service.findProfile(actor(authentication)));
    }

    @PutMapping
    @Operation(
            summary = "Update the authenticated staff self-profile",
            description = "Only firstName and lastName may be changed. Requires the current "
                    + "profile version and a valid CSRF token.")
    @ApiResponse(responseCode = "200", description = "Profile updated")
    @ApiResponse(responseCode = "400", description = "Invalid profile request")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Valid CSRF token required")
    @ApiResponse(responseCode = "409", description = "Profile version conflict")
    StaffSelfProfileResponse update(
            @Valid @RequestBody UpdateStaffSelfProfileRequest request,
            Authentication authentication) {
        return StaffSelfProfileResponse.from(
                service.update(actor(authentication), request.toCommand()));
    }

    @PutMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload or replace the authenticated staff photo",
            description = "Accepts JPEG, PNG, or WebP files up to 5 MiB. The file signature "
                    + "and checksum are validated server-side. Requires the current profile "
                    + "version and CSRF.")
    @ApiResponse(responseCode = "200", description = "Photo metadata returned",
            content = @Content(schema = @Schema(implementation = StaffProfilePhotoDetails.class)))
    @ApiResponse(responseCode = "400", description = "Invalid photo or version")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Valid CSRF token required")
    @ApiResponse(responseCode = "409", description = "Profile version conflict")
    @ApiResponse(responseCode = "413", description = "Photo is too large")
    StaffProfilePhotoDetails uploadPhoto(
            @Parameter(
                    description = "JPEG, PNG, or WebP image; maximum size 5 MiB.",
                    required = true,
                    content = @Content(mediaType = "image/*",
                            schema = @Schema(type = "string", format = "binary",
                                    maxLength = 5242880)))
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Current profile version used for optimistic locking.",
                    required = true)
            @RequestParam("version") long version,
            Authentication authentication) throws java.io.IOException {
        StaffProfilePhotoContent content = new StaffProfilePhotoContent(
                file.getContentType(), file.getBytes());
        return service.uploadPhoto(
                actor(authentication),
                new UploadStaffProfilePhotoCommand(content, version));
    }

    @GetMapping(value = "/photo", produces = {
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp"})
    @Operation(
            summary = "Download the authenticated staff photo",
            description = "Returns the private photo through the application authorization "
                    + "boundary. The response is never publicly cacheable.")
    @ApiResponse(responseCode = "200", description = "Photo returned",
            content = @Content(mediaType = "image/*",
                    schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "404", description = "Photo not found")
    ResponseEntity<byte[]> downloadPhoto(Authentication authentication) {
        StaffProfilePhotoContent content = service.downloadPhoto(actor(authentication));
        byte[] bytes = content.bytes();
        String extension = extension(content.contentType());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(bytes.length)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"profile-photo" + extension + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }

    @DeleteMapping("/photo")
    @Operation(
            summary = "Remove the authenticated staff photo",
            description = "Clears the canonical photo metadata and removes the private object "
                    + "after the versioned database transition. Requires CSRF.")
    @ApiResponse(responseCode = "200", description = "Profile returned without a photo")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Valid CSRF token required")
    @ApiResponse(responseCode = "409", description = "Profile version conflict")
    StaffSelfProfileResponse removePhoto(
            @RequestParam("version") long version,
            Authentication authentication) {
        return StaffSelfProfileResponse.from(
                service.removePhoto(actor(authentication), version));
    }

    @PostMapping("/password")
    @Operation(
            summary = "Change the authenticated staff password",
            description = "Requires the current password, the configured password policy, "
                    + "confirmation, and CSRF. The current session requires reauthentication; "
                    + "password values are never returned.")
    @ApiResponse(responseCode = "200", description = "Password changed",
            content = @Content(schema = @Schema(implementation = StaffPasswordChangeResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid password or confirmation")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Valid CSRF token required")
    StaffPasswordChangeResponse changePassword(
            @Valid @RequestBody ChangeStaffPasswordRequest request,
            Authentication authentication) {
        StaffPasswordChangeResult result = service.changePassword(
                actor(authentication), request.toCommand());
        return StaffPasswordChangeResponse.from(result);
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedActorProvider principal)) {
            throw new IllegalStateException("Authenticated staff principal is required.");
        }
        return principal.authenticatedActor();
    }

    private static String extension(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".img";
        };
    }
}
