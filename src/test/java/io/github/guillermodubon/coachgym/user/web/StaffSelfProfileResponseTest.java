package io.github.guillermodubon.coachgym.user.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffProfilePhotoDetails;
import io.github.guillermodubon.coachgym.user.StaffSelfProfileDetails;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffSelfProfileResponseTest {

    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000007301");

    @Test
    void mapsHeaderSafeIdentityAndRelativePrivatePhotoUrl() {
        StaffSelfProfileDetails profile = new StaffSelfProfileDetails(
                USER_ID,
                "admin",
                "admin@example.test",
                "Ana",
                "Martinez",
                Set.of(RoleCode.ADMIN),
                StaffAccountStatus.ACTIVE,
                new StaffProfilePhotoDetails(
                        UUID.randomUUID(),
                        "image/png",
                        42,
                        Instant.parse("2026-09-19T12:00:00Z"),
                        2),
                2);

        StaffSelfProfileResponse response = StaffSelfProfileResponse.from(profile);

        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.displayName()).isEqualTo("Ana Martinez");
        assertThat(response.roles()).containsExactly(RoleCode.ADMIN);
        assertThat(response.photoPresent()).isTrue();
        assertThat(response.photoUrl()).hasToString("/api/v1/me/profile/photo");
        assertThat(response.toString()).doesNotContain(
                "password", "hash", "storageKey", "checksum");
    }
}
