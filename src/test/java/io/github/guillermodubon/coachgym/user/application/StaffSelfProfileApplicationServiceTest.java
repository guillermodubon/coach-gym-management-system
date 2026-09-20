package io.github.guillermodubon.coachgym.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffPasswordChanged;
import io.github.guillermodubon.coachgym.user.StaffProfilePhotoChanged;
import io.github.guillermodubon.coachgym.user.StaffProfilePhotoDetails;
import io.github.guillermodubon.coachgym.user.StaffProfileUpdated;
import io.github.guillermodubon.coachgym.user.StaffSelfProfileDetails;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Method;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class StaffSelfProfileApplicationServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final AuthenticatedActor ACTOR = new AuthenticatedActor(USER_ID, "admin");
    private static final Instant NOW = Instant.parse("2026-09-19T12:00:00Z");

    @Mock
    private StaffProfileQuery profileQuery;
    @Mock
    private StaffProfileStore profileStore;
    @Mock
    private StaffProfilePhotoStore photoStore;
    @Mock
    private StaffProfilePhotoStorage photoStorage;
    @Mock
    private StaffPasswordStore passwordStore;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private StaffSelfProfileApplicationService service;
    private StaffSelfProfileDetails profile;

    @BeforeEach
    void setUp() {
        service = new StaffSelfProfileApplicationService(
                profileQuery,
                profileStore,
                photoStore,
                photoStorage,
                passwordStore,
                passwordEncoder,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        profile = profile("Ana", "Martinez", 4, null);
    }

    @Test
    void updateUsesAuthenticatedActorAndPublishesOnlyChangedFieldNames() {
        StaffSelfProfileDetails updated = profile("Ana", "Hernandez", 5, null);
        when(profileQuery.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(profileStore.update(eq(USER_ID), any())).thenReturn(updated);

        StaffSelfProfileDetails result = service.update(
                ACTOR,
                new UpdateStaffSelfProfileCommand(" Ana ", " Hernandez ", 4));

        assertThat(result).isSameAs(updated);
        ArgumentCaptor<StaffProfileUpdated> event =
                ArgumentCaptor.forClass(StaffProfileUpdated.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().userId()).isEqualTo(USER_ID);
        assertThat(event.getValue().changedFields()).containsExactly("lastName");
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void noOpUpdateDoesNotWriteOrPublishAnEvent() {
        when(profileQuery.findByUserId(USER_ID)).thenReturn(Optional.of(profile));

        assertThat(service.update(
                ACTOR,
                new UpdateStaffSelfProfileCommand("Ana", "Martinez", 4)))
                .isSameAs(profile);

        verify(profileStore, never()).update(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void staleUpdateIsRejectedBeforeTheWritePortIsCalled() {
        when(profileQuery.findByUserId(USER_ID)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.update(
                ACTOR,
                new UpdateStaffSelfProfileCommand("Ana", "Hernandez", 3)))
                .isInstanceOf(StaffProfileVersionConflictException.class);

        verify(profileStore, never()).update(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void passwordChangeRequiresCurrentPasswordAndDoesNotPublishOnFailure() {
        when(profileQuery.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(passwordStore.findPasswordHash(USER_ID)).thenReturn(Optional.of("{bcrypt}hash"));
        when(passwordEncoder.matches("wrong-password", "{bcrypt}hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(
                ACTOR,
                new ChangeStaffPasswordCommand(
                        "wrong-password", "new-password-123", "new-password-123")))
                .isInstanceOf(StaffCurrentPasswordInvalidException.class);

        verify(passwordStore, never()).updatePassword(any(), any(), any(Long.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void passwordChangeEncodesNewValueAndRequiresReauthentication() {
        StaffSelfProfileDetails updated = profile("Ana", "Martinez", 5, null);
        when(profileQuery.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(passwordStore.findPasswordHash(USER_ID)).thenReturn(Optional.of("{bcrypt}hash"));
        when(passwordEncoder.matches("current-password", "{bcrypt}hash")).thenReturn(true);
        when(passwordEncoder.matches("new-password-123", "{bcrypt}hash")).thenReturn(false);
        when(passwordEncoder.encode("new-password-123")).thenReturn("{bcrypt}encoded");
        when(passwordStore.updatePassword(USER_ID, "{bcrypt}encoded", 4))
                .thenReturn(updated);

        StaffPasswordChangeResult result = service.changePassword(
                ACTOR,
                new ChangeStaffPasswordCommand(
                        "current-password", "new-password-123", "new-password-123"));

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.profileVersion()).isEqualTo(5);
        assertThat(result.reauthenticationRequired()).isTrue();
        verify(passwordEncoder).encode("new-password-123");
        ArgumentCaptor<StaffPasswordChanged> event =
                ArgumentCaptor.forClass(StaffPasswordChanged.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().userId()).isEqualTo(USER_ID);
        assertThat(event.getValue().reauthenticationRequired()).isTrue();
        assertThat(event.getValue().toString()).doesNotContain(
                "current-password", "new-password-123", "{bcrypt}");
    }

    @Test
    void passwordReuseIsRejectedEvenWhenEncoderUsesAUniqueSalt() {
        when(profileQuery.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(passwordStore.findPasswordHash(USER_ID)).thenReturn(Optional.of("{bcrypt}hash"));
        when(passwordEncoder.matches("current-password", "{bcrypt}hash")).thenReturn(true);
        when(passwordEncoder.matches("new-password-123", "{bcrypt}hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(
                ACTOR,
                new ChangeStaffPasswordCommand(
                        "current-password", "new-password-123", "new-password-123")))
                .isInstanceOf(StaffProfileValidationException.class);

        verify(passwordEncoder, never()).encode(any());
        verify(passwordStore, never()).updatePassword(any(), any(), any(Long.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void applicationMutationsRequireSupportedRolesAndTransactionalProfileUpdates() throws Exception {
        for (String methodName : Set.of("findProfile", "update", "uploadPhoto", "removePhoto",
                "changePassword")) {
            Method method = java.util.Arrays.stream(
                            StaffSelfProfileApplicationService.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            assertThat(method.getAnnotation(PreAuthorize.class).value())
                    .isEqualTo("hasAnyRole('ADMIN', 'RECEPTIONIST')");
        }
        assertThat(StaffSelfProfileApplicationService.class
                .getDeclaredMethod("findProfile", AuthenticatedActor.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(StaffSelfProfileApplicationService.class
                .getDeclaredMethod("update", AuthenticatedActor.class,
                        UpdateStaffSelfProfileCommand.class)
                .getAnnotation(Transactional.class).readOnly()).isFalse();
        assertThat(StaffSelfProfileApplicationService.class
                .getDeclaredMethod("changePassword", AuthenticatedActor.class,
                        ChangeStaffPasswordCommand.class)
                .getAnnotation(Transactional.class).readOnly()).isFalse();
    }

    @Test
    void photoPersistenceFailureDeletesTheStagedObject() throws Exception {
        StaffProfilePhotoContent content = pngContent();
        when(profileQuery.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(photoStore.findPhotoByUserId(USER_ID)).thenReturn(Optional.empty());
        when(photoStorage.generateStorageKey(USER_ID, "image/png"))
                .thenReturn("staff-profiles/10000000-0000-0000-0000-000000000001/20000000-0000-0000-0000-000000000001.png");
        StaffProfileDataAccessException failure = new StaffProfileDataAccessException(
                "metadata failed.", new IllegalStateException("test"));
        when(photoStore.save(
                eq(USER_ID), any(), eq("image/png"), eq(content.sizeBytes()),
                eq(content.checksumSha256()), eq(4L), eq(ACTOR), eq(NOW)))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.uploadPhoto(
                ACTOR, new UploadStaffProfilePhotoCommand(content, 4)))
                .isSameAs(failure);

        verify(photoStorage).store(any(), eq(content));
        verify(photoStorage).delete(
                "staff-profiles/10000000-0000-0000-0000-000000000001/20000000-0000-0000-0000-000000000001.png");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void photoReplacementSwitchesMetadataBeforeCleaningTheOldObjectAndPublishesSafeEvent()
            throws Exception {
        StaffProfilePhotoContent content = pngContent();
        StaffProfilePhotoDetails oldDetails = new StaffProfilePhotoDetails(
                UUID.randomUUID(), "image/png", content.sizeBytes(), NOW, 4);
        StaffProfilePhotoRecord previous = new StaffProfilePhotoRecord(
                oldDetails,
                "staff-profiles/10000000-0000-0000-0000-000000000001/30000000-0000-0000-0000-000000000001.png",
                "a".repeat(64));
        StaffProfilePhotoDetails saved = new StaffProfilePhotoDetails(
                UUID.randomUUID(), "image/png", content.sizeBytes(), NOW, 5);
        when(profileQuery.findByUserId(USER_ID)).thenReturn(Optional.of(
                profile("Ana", "Martinez", 4, oldDetails)));
        when(photoStore.findPhotoByUserId(USER_ID)).thenReturn(Optional.of(previous));
        when(photoStorage.generateStorageKey(USER_ID, "image/png"))
                .thenReturn("staff-profiles/10000000-0000-0000-0000-000000000001/40000000-0000-0000-0000-000000000001.png");
        when(photoStore.save(
                eq(USER_ID), any(), eq("image/png"), eq(content.sizeBytes()),
                eq(content.checksumSha256()), eq(4L), eq(ACTOR), eq(NOW)))
                .thenReturn(saved);

        assertThat(service.uploadPhoto(
                ACTOR, new UploadStaffProfilePhotoCommand(content, 4)))
                .isSameAs(saved);

        verify(photoStorage).store(
                "staff-profiles/10000000-0000-0000-0000-000000000001/40000000-0000-0000-0000-000000000001.png", content);
        verify(photoStorage).delete(
                "staff-profiles/10000000-0000-0000-0000-000000000001/30000000-0000-0000-0000-000000000001.png");
        ArgumentCaptor<StaffProfilePhotoChanged> event =
                ArgumentCaptor.forClass(StaffProfilePhotoChanged.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().userId()).isEqualTo(USER_ID);
        assertThat(event.getValue().photoPresent()).isTrue();
    }

    @Test
    void removingAPhotoIsIdempotentWhenMetadataIsAlreadyAbsent() {
        when(profileQuery.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(photoStore.findPhotoByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.removePhoto(ACTOR, 4)).isSameAs(profile);

        verify(photoStore, never()).delete(any(), any(Long.class));
        verify(photoStorage, never()).delete(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void removingAPhotoDeletesCanonicalMetadataAndPublishesPresenceTransition()
            throws Exception {
        StaffProfilePhotoContent content = pngContent();
        StaffProfilePhotoDetails oldDetails = new StaffProfilePhotoDetails(
                UUID.randomUUID(), "image/png", content.sizeBytes(), NOW, 4);
        StaffProfilePhotoRecord previous = new StaffProfilePhotoRecord(
                oldDetails,
                "staff-profiles/10000000-0000-0000-0000-000000000001/50000000-0000-0000-0000-000000000001.png",
                "a".repeat(64));
        StaffSelfProfileDetails withPhoto = profile("Ana", "Martinez", 4, oldDetails);
        StaffSelfProfileDetails withoutPhoto = profile("Ana", "Martinez", 5, null);
        when(profileQuery.findByUserId(USER_ID))
                .thenReturn(Optional.of(withPhoto), Optional.of(withoutPhoto));
        when(photoStore.findPhotoByUserId(USER_ID)).thenReturn(Optional.of(previous));
        when(photoStore.delete(USER_ID, 4)).thenReturn(previous.storageKey());

        assertThat(service.removePhoto(ACTOR, 4)).isSameAs(withoutPhoto);

        verify(photoStore).delete(USER_ID, 4);
        verify(photoStorage).delete(previous.storageKey());
        ArgumentCaptor<StaffProfilePhotoChanged> event =
                ArgumentCaptor.forClass(StaffProfilePhotoChanged.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().photoPresent()).isFalse();
    }

    private static StaffSelfProfileDetails profile(
            String firstName,
            String lastName,
            long version,
            StaffProfilePhotoDetails photo) {
        return new StaffSelfProfileDetails(
                USER_ID,
                "admin",
                "admin@example.com",
                firstName,
                lastName,
                Set.of(RoleCode.ADMIN),
                StaffAccountStatus.ACTIVE,
                photo,
                version);
    }

    private static StaffProfilePhotoContent pngContent() throws Exception {
        byte[] bytes = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        return new StaffProfilePhotoContent(
                "image/png",
                bytes,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
}
