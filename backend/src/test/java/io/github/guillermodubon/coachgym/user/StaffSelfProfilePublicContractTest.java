package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.user.application.ChangeStaffPasswordCommand;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoContent;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoStorage;
import io.github.guillermodubon.coachgym.user.application.StaffProfileQuery;
import io.github.guillermodubon.coachgym.user.application.StaffProfileStore;
import io.github.guillermodubon.coachgym.user.application.StaffProfileValidationException;
import io.github.guillermodubon.coachgym.user.application.UpdateStaffSelfProfileCommand;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffSelfProfilePublicContractTest {

    private static final UUID USER_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");

    @Test
    void editablePolicyContainsOnlyExistingPersonalNameColumns() {
        assertThat(StaffSelfProfilePolicy.editableFields())
                .containsExactlyInAnyOrder("firstName", "lastName");
        assertThat(StaffSelfProfilePolicy.editableFields())
                .doesNotContain("username", "email", "role", "status",
                        "organizationScope", "branchAssignments", "permissions",
                        "userId", "passwordHash", "version", "phone", "preferredLocale");
        assertThat(StaffSelfProfilePolicy.prohibitedFields())
                .contains("username", "email", "role", "status",
                        "organizationScope", "branchAssignments", "permissions");
        assertThat(StaffSelfProfilePolicy.isEditable("firstName")).isTrue();
        assertThat(StaffSelfProfilePolicy.isEditable("role")).isFalse();
        assertThat(StaffSelfProfilePolicy.isEditable(null)).isFalse();
    }

    @Test
    void updateCommandNormalizesNamesAndRequiresExpectedVersion() {
        UpdateStaffSelfProfileCommand command = new UpdateStaffSelfProfileCommand(
                "  Ana ", " Martínez ", 4);

        assertThat(command.firstName()).isEqualTo("Ana");
        assertThat(command.lastName()).isEqualTo("Martínez");
        assertThat(command.expectedVersion()).isEqualTo(4);
        assertThat(componentNames(UpdateStaffSelfProfileCommand.class))
                .containsExactlyInAnyOrder("firstName", "lastName", "expectedVersion");
    }

    @Test
    void updateCommandRejectsInvalidNamesAndStaleVersionMarker() {
        assertThatThrownBy(() -> new UpdateStaffSelfProfileCommand(" ", "Smith", 0))
                .isInstanceOf(StaffProfileValidationException.class);
        assertThatThrownBy(() -> new UpdateStaffSelfProfileCommand(
                "A".repeat(101), "Smith", 0))
                .isInstanceOf(StaffProfileValidationException.class);
        assertThatThrownBy(() -> new UpdateStaffSelfProfileCommand("Ana", "Smith", -1))
                .isInstanceOf(StaffProfileValidationException.class);
    }

    @Test
    void passwordCommandValidatesStrengthConfirmationAndReuseWithoutLeakingSecrets() {
        ChangeStaffPasswordCommand command = new ChangeStaffPasswordCommand(
                "current-password", "new-password-123", "new-password-123");

        assertThat(command.toString()).doesNotContain(
                "current-password", "new-password-123");
        assertThat(componentNames(ChangeStaffPasswordCommand.class))
                .containsExactlyInAnyOrder(
                        "currentPassword", "newPassword", "newPasswordConfirmation");
        assertThatThrownBy(() -> new ChangeStaffPasswordCommand(
                "current-password", "short", "short"))
                .isInstanceOf(StaffProfileValidationException.class);
        assertThatThrownBy(() -> new ChangeStaffPasswordCommand(
                "current-password", "new-password-123", "different-password"))
                .isInstanceOf(StaffProfileValidationException.class);
        assertThatThrownBy(() -> new ChangeStaffPasswordCommand(
                "same-password", "same-password", "same-password"))
                .isInstanceOf(StaffProfileValidationException.class);
    }

    @Test
    void profileProjectionIsImmutableAndExcludesStorageInternals() {
        StaffSelfProfileDetails profile = new StaffSelfProfileDetails(
                USER_ID,
                "admin",
                "admin@example.com",
                "Ana",
                "Martinez",
                Set.of(RoleCode.ADMIN),
                StaffAccountStatus.ACTIVE,
                null,
                3);

        assertThat(profile.displayName()).isEqualTo("Ana Martinez");
        assertThat(profile.photoPresent()).isFalse();
        assertThatThrownBy(() -> profile.roles().add(RoleCode.RECEPTIONIST))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(componentNames(StaffSelfProfileDetails.class))
                .doesNotContain("passwordHash", "sessionId", "storageKey",
                        "organizationId", "branchAssignments");
    }

    @Test
    void photoMetadataAndStorageContentAreBoundedAndDefensivelyCopied() throws Exception {
        byte[] source = "profile-photo".getBytes(StandardCharsets.UTF_8);
        StaffProfilePhotoContent content = new StaffProfilePhotoContent(
                "IMAGE/PNG", source, checksum(source));
        source[0] = 0;
        byte[] returned = content.bytes();
        returned[1] = 0;

        assertThat(content.contentType()).isEqualTo("image/png");
        assertThat(content.sizeBytes()).isEqualTo("profile-photo".length());
        assertThat(content.bytes()).isNotEqualTo(returned);
        assertThat(content.toString()).doesNotContain("profile-photo");
        assertThatThrownBy(() -> new StaffProfilePhotoContent(
                "image/svg+xml", new byte[]{1}, "0".repeat(64)))
                .isInstanceOf(StaffProfileValidationException.class);

        StaffProfilePhotoDetails details = new StaffProfilePhotoDetails(
                USER_ID, "image/png", content.sizeBytes(),
                java.time.Instant.parse("2026-09-19T12:00:00Z"), 0);
        assertThat(details.toString()).doesNotContain("storageKey");
    }

    @Test
    void applicationPortsExposeOnlyTechnologyNeutralTypes() {
        assertThat(StaffProfileQuery.class.getPackageName())
                .isEqualTo("io.github.guillermodubon.coachgym.user.application");
        assertThat(StaffProfileStore.class.getPackageName())
                .isEqualTo("io.github.guillermodubon.coachgym.user.application");
        assertThat(StaffProfilePhotoStorage.class.getPackageName())
                .isEqualTo("io.github.guillermodubon.coachgym.user.application");
        for (Class<?> type : List.of(
                StaffSelfProfileDetails.class,
                StaffProfilePhotoDetails.class,
                UpdateStaffSelfProfileCommand.class,
                ChangeStaffPasswordCommand.class,
                StaffProfilePhotoContent.class)) {
            assertThat(type.isRecord()).isTrue();
            Arrays.stream(type.getDeclaredFields()).forEach(field ->
                    assertThat(field.getType().getName())
                            .doesNotContain("org.springframework", "jakarta.",
                                    ".infrastructure", "com.stripe.", "com.resend."));
        }
    }

    private static Set<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String checksum(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
