package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffSelfProfileDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.util.Set;
import java.util.UUID;

/** Stable HTTP projection for the authenticated staff self-profile. */
@Schema(name = "StaffSelfProfile")
record StaffSelfProfileResponse(
        UUID userId,
        String username,
        String email,
        String firstName,
        String lastName,
        String displayName,
        Set<RoleCode> roles,
        StaffAccountStatus status,
        boolean photoPresent,
        URI photoUrl,
        long version) {

    static StaffSelfProfileResponse from(StaffSelfProfileDetails details) {
        URI photoUrl = details.photoPresent()
                ? URI.create("/api/v1/me/profile/photo")
                : null;
        return new StaffSelfProfileResponse(
                details.userId(),
                details.username(),
                details.email(),
                details.firstName(),
                details.lastName(),
                details.displayName(),
                details.roles(),
                details.status(),
                details.photoPresent(),
                photoUrl,
                details.version());
    }
}
