package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.StaffPasswordChanged;
import io.github.guillermodubon.coachgym.user.StaffProfilePhotoChanged;
import io.github.guillermodubon.coachgym.user.StaffProfilePhotoDetails;
import io.github.guillermodubon.coachgym.user.StaffProfileUpdated;
import io.github.guillermodubon.coachgym.user.StaffSelfProfileDetails;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Actor-derived self-profile use cases for the two supported staff roles.
 *
 * <p>External object storage is deliberately kept outside a service
 * transaction. The metadata ports provide short database transactions and
 * the photo workflow compensates a staged object when the metadata switch
 * fails.</p>
 */
@Service
public class StaffSelfProfileApplicationService {

    private static final boolean REAUTHENTICATION_REQUIRED = true;

    private final StaffProfileQuery profileQuery;
    private final StaffProfileStore profileStore;
    private final StaffProfilePhotoStore photoStore;
    private final StaffProfilePhotoStorage photoStorage;
    private final StaffPasswordStore passwordStore;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public StaffSelfProfileApplicationService(
            StaffProfileQuery profileQuery,
            StaffProfileStore profileStore,
            StaffProfilePhotoStore photoStore,
            StaffProfilePhotoStorage photoStorage,
            StaffPasswordStore passwordStore,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.profileQuery = Objects.requireNonNull(profileQuery);
        this.profileStore = Objects.requireNonNull(profileStore);
        this.photoStore = Objects.requireNonNull(photoStore);
        this.photoStorage = Objects.requireNonNull(photoStorage);
        this.passwordStore = Objects.requireNonNull(passwordStore);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public StaffSelfProfileDetails findProfile(AuthenticatedActor actor) {
        return loadProfile(requireActor(actor));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public StaffSelfProfileDetails update(
            AuthenticatedActor actor,
            UpdateStaffSelfProfileCommand command) {
        UUID userId = requireActor(actor);
        if (command == null) {
            throw new StaffProfileValidationException(
                    "Staff profile update command is required.");
        }
        StaffSelfProfileDetails current = loadProfile(userId);
        requireExpectedVersion(userId, current.version(), command.expectedVersion());
        Set<String> changedFields = changedFields(current, command);
        if (changedFields.isEmpty()) {
            return current;
        }

        StaffSelfProfileDetails updated = profileStore.update(userId, command);
        Instant occurredAt = clock.instant();
        eventPublisher.publishEvent(new StaffProfileUpdated(
                userId, changedFields, occurredAt, actor.username()));
        return updated;
    }

    /** Stages a new private object before switching canonical photo metadata. */
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public StaffProfilePhotoDetails uploadPhoto(
            AuthenticatedActor actor,
            UploadStaffProfilePhotoCommand command) {
        UUID userId = requireActor(actor);
        if (command == null) {
            throw new StaffProfileValidationException(
                    "Staff profile photo command is required.");
        }
        StaffSelfProfileDetails current = loadProfile(userId);
        requireExpectedVersion(userId, current.version(), command.expectedProfileVersion());
        StaffProfilePhotoContent content = command.content();
        StaffProfilePhotoInspector.requireSafe(content);
        StaffProfilePhotoRecord previous = photoStore.findPhotoByUserId(userId).orElse(null);
        String newStorageKey = photoStorage.generateStorageKey(
                userId, content.contentType());
        StaffProfilePhotoStorageKey.requireCanonicalForContentType(
                newStorageKey, content.contentType());
        photoStorage.store(newStorageKey, content);

        StaffProfilePhotoDetails saved;
        try {
            saved = photoStore.save(
                    userId,
                    newStorageKey,
                    content.contentType(),
                    content.sizeBytes(),
                    content.checksumSha256(),
                    command.expectedProfileVersion(),
                    actor,
                    clock.instant());
        } catch (RuntimeException exception) {
            compensateNewObject(newStorageKey, exception);
            throw exception;
        }

        StaffProfilePhotoCleanupException cleanupFailure = null;
        if (previous != null && !previous.storageKey().equals(newStorageKey)) {
            try {
                photoStorage.delete(previous.storageKey());
            } catch (RuntimeException exception) {
                cleanupFailure = new StaffProfilePhotoCleanupException(exception);
            }
        }
        eventPublisher.publishEvent(new StaffProfilePhotoChanged(
                userId, true, clock.instant(), actor.username()));
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
        return saved;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public StaffProfilePhotoContent downloadPhoto(AuthenticatedActor actor) {
        UUID userId = requireActor(actor);
        StaffProfilePhotoRecord record = photoStore.findPhotoByUserId(userId)
                .orElseThrow(StaffProfilePhotoNotFoundException::new);
        return photoStorage.load(
                record.storageKey(),
                record.details().contentType(),
                record.details().sizeBytes(),
                record.checksumSha256());
    }

    /**
     * Removes the canonical photo metadata first, then deletes the private
     * object. Repeating the operation when no photo exists is an idempotent
     * no-op after the optimistic version has been checked.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public StaffSelfProfileDetails removePhoto(
            AuthenticatedActor actor,
            long expectedProfileVersion) {
        UUID userId = requireActor(actor);
        StaffSelfProfileDetails current = loadProfile(userId);
        requireExpectedVersion(userId, current.version(), expectedProfileVersion);
        Optional<StaffProfilePhotoRecord> previous = photoStore.findPhotoByUserId(userId);
        if (previous.isEmpty()) {
            return current;
        }

        String oldStorageKey = photoStore.delete(userId, expectedProfileVersion);
        StaffProfilePhotoCleanupException cleanupFailure = null;
        try {
            photoStorage.delete(oldStorageKey);
        } catch (RuntimeException exception) {
            cleanupFailure = new StaffProfilePhotoCleanupException(exception);
        }
        eventPublisher.publishEvent(new StaffProfilePhotoChanged(
                userId, false, clock.instant(), actor.username()));
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
        return loadProfile(userId);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public StaffPasswordChangeResult changePassword(
            AuthenticatedActor actor,
            ChangeStaffPasswordCommand command) {
        UUID userId = requireActor(actor);
        if (command == null) {
            throw new StaffProfileValidationException(
                    "Staff password command is required.");
        }
        StaffSelfProfileDetails current = loadProfile(userId);
        String passwordHash = passwordStore.findPasswordHash(userId)
                .orElseThrow(StaffProfileNotFoundException::new);
        if (!matches(command.currentPassword(), passwordHash)) {
            throw new StaffCurrentPasswordInvalidException();
        }
        if (matches(command.newPassword(), passwordHash)) {
            throw new StaffProfileValidationException(
                    "New password must differ from the current password.");
        }

        StaffSelfProfileDetails updated = passwordStore.updatePassword(
                userId,
                passwordEncoder.encode(command.newPassword()),
                current.version());
        Instant occurredAt = clock.instant();
        eventPublisher.publishEvent(new StaffPasswordChanged(
                userId, REAUTHENTICATION_REQUIRED, occurredAt, actor.username()));
        return new StaffPasswordChangeResult(
                updated.userId(), updated.version(), REAUTHENTICATION_REQUIRED);
    }

    private StaffSelfProfileDetails loadProfile(UUID userId) {
        return profileQuery.findByUserId(userId)
                .orElseThrow(StaffProfileNotFoundException::new);
    }

    private static Set<String> changedFields(
            StaffSelfProfileDetails current,
            UpdateStaffSelfProfileCommand command) {
        java.util.LinkedHashSet<String> changed = new java.util.LinkedHashSet<>();
        if (!current.firstName().equals(command.firstName())) {
            changed.add("firstName");
        }
        if (!current.lastName().equals(command.lastName())) {
            changed.add("lastName");
        }
        return Set.copyOf(changed);
    }

    private static void requireExpectedVersion(
            UUID userId,
            long currentVersion,
            long expectedVersion) {
        if (currentVersion != expectedVersion) {
            throw new StaffProfileVersionConflictException(userId);
        }
    }

    private boolean matches(String rawPassword, String encodedPassword) {
        try {
            return passwordEncoder.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void compensateNewObject(String storageKey, RuntimeException original) {
        try {
            photoStorage.delete(storageKey);
        } catch (RuntimeException compensationFailure) {
            original.addSuppressed(compensationFailure);
        }
    }

    private static UUID requireActor(AuthenticatedActor actor) {
        if (actor == null || actor.id() == null) {
            throw new StaffProfileValidationException("Authenticated actor is required.");
        }
        return actor.id();
    }
}
