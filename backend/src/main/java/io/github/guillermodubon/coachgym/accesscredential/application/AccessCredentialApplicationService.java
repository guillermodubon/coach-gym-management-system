package io.github.guillermodubon.coachgym.accesscredential.application;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDetails;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDocument;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialHistoryDetails;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialIssued;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialQrPayload;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialReplaced;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialRevoked;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialTokenProtector;
import io.github.guillermodubon.coachgym.client.ClientAccessDetails;
import io.github.guillermodubon.coachgym.client.ClientAccessQuery;
import io.github.guillermodubon.coachgym.accesscredential.domain.AccessCredentialPolicy;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.BranchOperationContext;
import io.github.guillermodubon.coachgym.user.BranchOperationContextResolver;
import io.github.guillermodubon.coachgym.user.BranchOwnedResourceReference;
import io.github.guillermodubon.coachgym.user.BranchResourceAuthorizationPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Coordinates the durable access-credential lifecycle across the database,
 * renderer, and artifact-storage ports.
 *
 * <p>The database remains authoritative for lifecycle state. Artifacts are
 * staged before metadata is committed and compensated when the transaction
 * cannot become canonical.</p>
 */
@Service
public class AccessCredentialApplicationService {

    private static final String CONTENT_TYPE = "image/png";
    private static final String TOKEN_SCHEME_VERSION_FALLBACK = "sha256-v1";
    private static final Logger LOGGER =
            LoggerFactory.getLogger(AccessCredentialApplicationService.class);

    private final AccessCredentialStore credentialStore;
    private final AccessCredentialQuery credentialQuery;
    private final AccessCredentialHistoryStore historyStore;
    private final AccessCredentialHistoryQuery historyQuery;
    private final ClientAccessQuery clientQuery;
    private final AccessCredentialTokenGenerator tokenGenerator;
    private final AccessCredentialTokenProtector tokenProtector;
    private final AccessCredentialQrRenderer qrRenderer;
    private final AccessCredentialStorage storage;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final BranchOperationContextResolver branchContextResolver;

    public AccessCredentialApplicationService(
            AccessCredentialStore credentialStore,
            AccessCredentialQuery credentialQuery,
            AccessCredentialHistoryStore historyStore,
            AccessCredentialHistoryQuery historyQuery,
            ClientAccessQuery clientQuery,
            AccessCredentialTokenGenerator tokenGenerator,
            AccessCredentialTokenProtector tokenProtector,
            AccessCredentialQrRenderer qrRenderer,
            AccessCredentialStorage storage,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this(
                credentialStore,
                credentialQuery,
                historyStore,
                historyQuery,
                clientQuery,
                tokenGenerator,
                tokenProtector,
                qrRenderer,
                storage,
                eventPublisher,
                clock,
                null);
    }

    @Autowired
    public AccessCredentialApplicationService(
            AccessCredentialStore credentialStore,
            AccessCredentialQuery credentialQuery,
            AccessCredentialHistoryStore historyStore,
            AccessCredentialHistoryQuery historyQuery,
            ClientAccessQuery clientQuery,
            AccessCredentialTokenGenerator tokenGenerator,
            AccessCredentialTokenProtector tokenProtector,
            AccessCredentialQrRenderer qrRenderer,
            AccessCredentialStorage storage,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            BranchOperationContextResolver branchContextResolver) {
        this.credentialStore = Objects.requireNonNull(credentialStore);
        this.credentialQuery = Objects.requireNonNull(credentialQuery);
        this.historyStore = Objects.requireNonNull(historyStore);
        this.historyQuery = Objects.requireNonNull(historyQuery);
        this.clientQuery = Objects.requireNonNull(clientQuery);
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator);
        this.tokenProtector = Objects.requireNonNull(tokenProtector);
        this.qrRenderer = Objects.requireNonNull(qrRenderer);
        this.storage = Objects.requireNonNull(storage);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
        this.branchContextResolver = branchContextResolver;
    }

    /**
     * Issues the one canonical active credential for an eligible client. A
     * concurrent request that loses the database uniqueness race returns the
     * winner's metadata and never publishes a duplicate event.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessCredentialDetails issue(
            IssueAccessCredentialCommand command,
            AuthenticatedActor actor) {
        if (command == null) {
            throw new AccessCredentialValidationException(
                    "Access credential issue command is required.");
        }
        requireActor(actor);

        credentialStore.lockClient(command.clientId());
        ClientAccessDetails client = clientQuery.findById(command.clientId())
                .orElseThrow(() -> new AccessCredentialClientNotFoundException(command.clientId()));
        if (!command.clientId().equals(client.id())) {
            throw new AccessCredentialDataAccessException(
                    "Client access projection does not match the requested client.", null);
        }
        UUID branchId = authorizeClientBranch(client, actor);
        AccessCredentialPolicy.requireIssueAllowed(client.id(), client.status());

        Optional<AccessCredentialDetails> existing =
                credentialStore.findActiveByClientIdForUpdate(command.clientId());
        if (existing.isPresent()) {
            return existing.get();
        }

        Instant issuedAt = serverNow();
        UUID credentialId = UUID.randomUUID();
        String credentialCode = credentialCode(credentialId);
        PreparedArtifact prepared = prepareArtifact(credentialId);
        try {
            AccessCredentialDetails persisted = persistIssued(
                    credentialId,
                    command.clientId(),
                    credentialCode,
                    issuedAt,
                    actor,
                    prepared);
            publishAfterCommit(new AccessCredentialIssued(
                    persisted.id(),
                    persisted.clientId(),
                    persisted.credentialCode(),
                    persisted.payloadVersion(),
                    prepared.tokenSchemeVersion(),
                    actor.id(),
                    actor.username(),
                    persisted.issuedAt(),
                    branchId),
                    prepared.storageKey());
            return persisted;
        } catch (AccessCredentialDuplicateException duplicate) {
            compensateOrThrow(prepared.storageKey(), duplicate);
            if (duplicate.kind() == AccessCredentialDuplicateException.Kind.ACTIVE_CLIENT) {
                return credentialQuery.findActiveByClientId(command.clientId())
                        .orElseThrow(() -> duplicate);
            }
            throw duplicate;
        } catch (RuntimeException failure) {
            compensateOrThrow(prepared.storageKey(), failure);
            throw failure;
        }
    }

    /** Reads nonsecret metadata for one credential, including final credentials. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessCredentialDetails findById(UUID credentialId) {
        requireIdentifier(credentialId, "Credential id");
        return credentialQuery.findById(credentialId)
                .orElseThrow(() -> new AccessCredentialNotFoundException(credentialId));
    }

    /** Reads the current active credential for one client. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessCredentialDetails findActiveByClientId(UUID clientId) {
        requireIdentifier(clientId, "Client id");
        return credentialQuery.findActiveByClientId(clientId)
                .orElseThrow(() -> new AccessCredentialNotFoundException(clientId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessCredentialDetails findActiveByClientId(
            UUID clientId,
            AuthenticatedActor actor) {
        requireActor(actor);
        authorizeClientBranch(clientId, actor);
        return findActiveByClientId(clientId);
    }

    /**
     * Loads the canonical active PNG after checking persisted artifact metadata.
     * The raw token is never reconstructed from the database.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessCredentialContent downloadActiveByClientId(UUID clientId) {
        requireLegacyDownloadAllowed();
        requireIdentifier(clientId, "Client id");
        return downloadActive(clientId, null);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessCredentialContent downloadActiveByClientId(
            UUID clientId,
            AuthenticatedActor actor) {
        requireActor(actor);
        authorizeClientBranch(clientId, actor);
        return downloadActive(clientId, actor);
    }

    /** Compatibility name for callers that address downloads by client. */
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessCredentialContent downloadByClientId(UUID clientId) {
        requireLegacyDownloadAllowed();
        requireIdentifier(clientId, "Client id");
        return downloadActive(clientId, null);
    }

    private void requireLegacyDownloadAllowed() {
        if (branchContextResolver != null) {
            throw new io.github.guillermodubon.coachgym.user.BranchResourceAuthorizationException();
        }
    }

    private AccessCredentialContent downloadActive(
            UUID clientId,
            AuthenticatedActor actor) {
        AccessCredentialDetails details = actor == null
                ? findActiveByClientId(clientId)
                : findActiveByClientId(clientId, actor);
        AccessCredentialStoredDocument metadata = credentialQuery
                .findArtifactByCredentialId(details.id())
                .orElseThrow(() -> new AccessCredentialDataAccessException(
                        "Access credential artifact metadata is missing.", null));
        AccessCredentialDocument document = storage.load(
                metadata.storageKey(),
                metadata.contentType(),
                metadata.sizeBytes(),
                metadata.checksumSha256());
        ensureArtifactMatches(metadata, document);
        return new AccessCredentialContent(details, document);
    }

    /**
     * Revokes the current credential for a client under the client lifecycle
     * lock. The lookup and transition are kept in the same transaction so a
     * concurrent request observes a stable state conflict instead of a
     * controller-level not-found result.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessCredentialDetails revoke(
            RevokeClientAccessCredentialCommand command,
            AuthenticatedActor actor) {
        if (command == null) {
            throw new AccessCredentialValidationException(
                    "Access credential revoke command is required.");
        }
        requireActor(actor);
        UUID branchId = authorizeClientBranch(command.clientId(), actor);
        AccessCredentialDetails current = loadCurrentForClient(command.clientId(), actor);
        return revokeLocked(
                new RevokeAccessCredentialCommand(
                        current.id(), command.reason(), command.expectedVersion()),
                actor,
                current,
                branchId);
    }

    /** Revokes an active credential while preserving its immutable PNG artifact. */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessCredentialDetails revoke(
            RevokeAccessCredentialCommand command,
            AuthenticatedActor actor) {
        if (command == null) {
            throw new AccessCredentialValidationException(
                    "Access credential revoke command is required.");
        }
        requireActor(actor);
        // Lifecycle mutations acquire locks in the same order (client, then
        // credential). Replacement already follows this order; revocation
        // must do the same to avoid a PostgreSQL deadlock when both requests
        // target the same client concurrently.
        AccessCredentialDetails hint = credentialQuery.findById(command.credentialId())
                .orElseThrow(() -> new AccessCredentialNotFoundException(command.credentialId()));
        UUID branchId = authorizeClientBranch(hint.clientId(), actor);
        credentialStore.lockClientForLifecycle(hint.clientId());
        AccessCredentialDetails current = credentialStore.findByIdForUpdate(command.credentialId())
                .orElseThrow(() -> new AccessCredentialNotFoundException(command.credentialId()));
        return revokeLocked(command, actor, current, branchId);
    }

    private AccessCredentialDetails revokeLocked(
            RevokeAccessCredentialCommand command,
            AuthenticatedActor actor,
            AccessCredentialDetails current,
            UUID branchId) {
        AccessCredentialPolicy.requireRevocationAllowed(current.id(), current.status());
        Instant revokedAt = serverNow();
        AccessCredentialDetails revoked = credentialStore.revoke(
                current.id(), command.reason(), actor.id(), revokedAt, command.expectedVersion());
        if (revoked == null
                || !current.id().equals(revoked.id())
                || !current.clientId().equals(revoked.clientId())
                || revoked.status() != AccessCredentialStatus.REVOKED
                || !actor.id().equals(revoked.revokedByUserId())
                || !revokedAt.equals(revoked.revokedAt())) {
            throw new AccessCredentialDataAccessException(
                    "Access credential revocation returned a noncanonical record.", null);
        }
        requireHistory(historyStore.append(new AccessCredentialHistoryPersistenceCommand(
                revoked.id(),
                revoked.clientId(),
                AccessCredentialStatus.ACTIVE,
                AccessCredentialStatus.REVOKED,
                command.reason(),
                revoked.revokedAt(),
                actor.id(),
                null)));
        publishAfterCommit(new AccessCredentialRevoked(
                revoked.id(),
                revoked.clientId(),
                revoked.credentialCode(),
                AccessCredentialStatus.ACTIVE,
                AccessCredentialStatus.REVOKED,
                actor.id(),
                actor.username(),
                revoked.revokedAt(),
                true,
                branchId));
        return revoked;
    }

    /**
     * Replaces the current credential for a client under the client lifecycle
     * lock. A final credential is reported as a state conflict rather than a
     * second not-found response when requests race.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessCredentialDetails replace(
            ReplaceClientAccessCredentialCommand command,
            AuthenticatedActor actor) {
        if (command == null) {
            throw new AccessCredentialValidationException(
                    "Access credential replacement command is required.");
        }
        requireActor(actor);
        UUID branchId = authorizeClientBranch(command.clientId(), actor);
        AccessCredentialDetails current = loadCurrentForClient(command.clientId(), actor);
        return replaceLocked(
                new ReplaceAccessCredentialCommand(
                        current.id(), command.reason(), command.expectedVersion()),
                actor,
                current,
                branchId);
    }

    /**
     * Atomically revokes the current credential and creates its replacement.
     * The old row remains REVOKED because the persisted status model has no
     * REPLACED value; the replacement link records the relationship.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessCredentialDetails replace(
            ReplaceAccessCredentialCommand command,
            AuthenticatedActor actor) {
        if (command == null) {
            throw new AccessCredentialValidationException(
                    "Access credential replacement command is required.");
        }
        requireActor(actor);

        AccessCredentialDetails hint = credentialQuery.findById(command.credentialId())
                .orElseThrow(() -> new AccessCredentialNotFoundException(command.credentialId()));
        UUID branchId = authorizeClientBranch(hint.clientId(), actor);
        credentialStore.lockClientForLifecycle(hint.clientId());
        AccessCredentialDetails current = credentialStore.findByIdForUpdate(command.credentialId())
                .orElseThrow(() -> new AccessCredentialNotFoundException(command.credentialId()));
        return replaceLocked(command, actor, current, branchId);
    }

    private AccessCredentialDetails replaceLocked(
            ReplaceAccessCredentialCommand command,
            AuthenticatedActor actor,
            AccessCredentialDetails current,
            UUID branchId) {
        AccessCredentialPolicy.requireReplacementAllowed(current.id(), current.status());

        Instant replacedAt = serverNow();
        UUID replacementId = UUID.randomUUID();
        PreparedArtifact prepared = prepareArtifact(replacementId);
        try {
            AccessCredentialDetails revoked = credentialStore.revoke(
                    current.id(), command.reason(), actor.id(), replacedAt,
                    command.expectedVersion());
            if (revoked == null
                    || !current.id().equals(revoked.id())
                    || !current.clientId().equals(revoked.clientId())
                    || revoked.status() != AccessCredentialStatus.REVOKED
                    || !actor.id().equals(revoked.revokedByUserId())
                    || !replacedAt.equals(revoked.revokedAt())) {
                throw new AccessCredentialDataAccessException(
                        "Access credential revocation returned a noncanonical record.", null);
            }
            requireHistory(historyStore.append(new AccessCredentialHistoryPersistenceCommand(
                    revoked.id(),
                    revoked.clientId(),
                    AccessCredentialStatus.ACTIVE,
                    AccessCredentialStatus.REVOKED,
                    command.reason(),
                    revoked.revokedAt(),
                    actor.id(),
                    null)));

            AccessCredentialDetails replacement = persistIssued(
                    replacementId,
                    current.clientId(),
                    credentialCode(replacementId),
                    replacedAt,
                    actor,
                    prepared);
            AccessCredentialDetails linked = credentialStore.attachReplacement(
                    revoked.id(), replacement.id(), revoked.version());
            if (linked == null
                    || linked.status() != AccessCredentialStatus.REVOKED
                    || !replacement.id().equals(linked.replacedByCredentialId())) {
                throw new AccessCredentialDataAccessException(
                        "Access credential replacement link is not canonical.", null);
            }
            publishAfterCommit(new AccessCredentialReplaced(
                    revoked.id(),
                    replacement.id(),
                    replacement.clientId(),
                    AccessCredentialStatus.REVOKED,
                    AccessCredentialStatus.ACTIVE,
                    actor.id(),
                    actor.username(),
                    replacedAt,
                    true,
                    branchId),
                    prepared.storageKey());
            return replacement;
        } catch (RuntimeException failure) {
            compensateOrThrow(prepared.storageKey(), failure);
            throw failure;
        }
    }

    /** Returns newest-first lifecycle history for a client. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessCredentialHistoryPage findHistoryByClientId(
            UUID clientId,
            int page,
            int size) {
        requireIdentifier(clientId, "Client id");
        if (page < 0 || size < 1 || size > 100) {
            throw new AccessCredentialValidationException(
                    "Credential history pagination is invalid.");
        }
        return historyQuery.findByClientId(clientId, page, size);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public AccessCredentialHistoryPage findHistoryByClientId(
            UUID clientId,
            int page,
            int size,
            AuthenticatedActor actor) {
        requireActor(actor);
        authorizeClientBranch(clientId, actor);
        return findHistoryByClientId(clientId, page, size);
    }

    private AccessCredentialDetails loadCurrentForClient(
            UUID clientId,
            AuthenticatedActor actor) {
        requireIdentifier(clientId, "Client id");
        authorizeClientBranch(clientId, actor);
        credentialStore.lockClientForLifecycle(clientId);
        return credentialStore.findActiveByClientIdForUpdate(clientId)
                .orElseGet(() -> credentialStore.findLatestByClientIdForUpdate(clientId)
                        .orElseThrow(() -> new AccessCredentialNotFoundException(clientId)));
    }

    private AccessCredentialDetails persistIssued(
            UUID credentialId,
            UUID clientId,
            String credentialCode,
            Instant issuedAt,
            AuthenticatedActor actor,
            PreparedArtifact prepared) {
        AccessCredentialDetails persisted = credentialStore.insert(
                new AccessCredentialPersistenceCommand(
                        credentialId,
                        clientId,
                        credentialCode,
                        prepared.fingerprint(),
                        prepared.tokenSchemeVersion(),
                        prepared.payloadVersion(),
                        issuedAt,
                        actor.id(),
                        prepared.storageKey(),
                        prepared.documentMetadata().contentType(),
                        prepared.documentMetadata().sizeBytes(),
                        prepared.documentMetadata().checksumSha256(),
                        prepared.documentMetadata().rendererVersion()));
        if (persisted == null) {
            throw new AccessCredentialDataAccessException(
                    "Access credential could not be persisted.", null);
        }
        ensureCanonical(persisted, credentialId, clientId, credentialCode, issuedAt, actor.id(),
                prepared);
        requireHistory(historyStore.append(new AccessCredentialHistoryPersistenceCommand(
                persisted.id(),
                persisted.clientId(),
                null,
                AccessCredentialStatus.ACTIVE,
                null,
                persisted.issuedAt(),
                actor.id(),
                null)));
        return persisted;
    }

    private PreparedArtifact prepareArtifact(UUID credentialId) {
        String token;
        try {
            token = tokenGenerator.generate();
        } catch (RuntimeException exception) {
            throw new AccessCredentialTokenGenerationException(
                    "Access credential token generation failed.", exception);
        }
        AccessCredentialQrPayload payload;
        try {
            payload = AccessCredentialQrPayload.fromToken(token);
        } catch (IllegalArgumentException exception) {
            throw new AccessCredentialTokenGenerationException(
                    "Access credential token generation failed.", exception);
        }
        String fingerprint;
        try {
            fingerprint = tokenProtector.fingerprint(payload.value());
        } catch (IllegalArgumentException exception) {
            throw new AccessCredentialValidationException(
                    "Access credential token protection failed.");
        }
        AccessCredentialDocument document = qrRenderer.render(payload);
        if (document == null) {
            throw new AccessCredentialRenderException(
                    "Access credential document could not be rendered.", null);
        }
        String storageKey = storage.generateStorageKey(credentialId);
        if (storageKey == null || storageKey.isBlank()) {
            throw new AccessCredentialStorageException(
                    "Access credential storage key is invalid.");
        }

        String rendererVersion = qrRenderer.rendererVersion();
        String tokenSchemeVersion = tokenProtector.schemeVersion();
        try {
            AccessCredentialStoredDocument metadata = storage.store(
                    storageKey,
                    document,
                    rendererVersion);
            if (metadata == null) {
                throw new AccessCredentialStorageException(
                        "Access credential storage returned no metadata.");
            }
            ensureStoredMetadata(metadata, storageKey, document, rendererVersion);
            return new PreparedArtifact(
                    metadata,
                    storageKey,
                    fingerprint,
                    payload.version(),
                    tokenSchemeVersion == null || tokenSchemeVersion.isBlank()
                            ? TOKEN_SCHEME_VERSION_FALLBACK
                            : tokenSchemeVersion.strip());
        } catch (RuntimeException failure) {
            compensateOrThrow(storageKey, failure);
            throw failure;
        }
    }

    private void publishAfterCommit(Object event, String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventPublisher.publishEvent(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publishEvent(event);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    try {
                        storage.delete(storageKey);
                    } catch (RuntimeException failure) {
                        LOGGER.error("Access credential artifact cleanup failed after rollback: {}",
                                failure.getClass().getSimpleName());
                    }
                }
            }
        });
    }

    private void publishAfterCommit(Object event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventPublisher.publishEvent(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publishEvent(event);
            }
        });
    }

    private void compensateOrThrow(String storageKey, RuntimeException failure) {
        try {
            storage.delete(storageKey);
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
            throw new AccessCredentialStorageException(
                    "Access credential artifact cleanup failed.", failure);
        }
    }

    private static void ensureCanonical(
            AccessCredentialDetails persisted,
            UUID credentialId,
            UUID clientId,
            String credentialCode,
            Instant issuedAt,
            UUID actorId,
            PreparedArtifact prepared) {
        if (!credentialId.equals(persisted.id())
                || !clientId.equals(persisted.clientId())
                || !credentialCode.equals(persisted.credentialCode())
                || persisted.status() != AccessCredentialStatus.ACTIVE
                || !prepared.payloadVersion().equals(persisted.payloadVersion())
                || !issuedAt.equals(persisted.issuedAt())
                || !actorId.equals(persisted.issuedByUserId())) {
            throw new AccessCredentialDataAccessException(
                    "Access credential persistence returned a noncanonical record.", null);
        }
    }

    private static void ensureStoredMetadata(
            AccessCredentialStoredDocument metadata,
            String storageKey,
            AccessCredentialDocument document,
            String rendererVersion) {
        boolean rendererMatches = rendererVersion == null || rendererVersion.isBlank()
                ? metadata.rendererVersion() == null
                : rendererVersion.strip().equals(metadata.rendererVersion());
        if (!storageKey.equals(metadata.storageKey())
                || !CONTENT_TYPE.equals(metadata.contentType())
                || metadata.sizeBytes() != document.sizeBytes()
                || !checksum(document.bytes()).equals(metadata.checksumSha256())
                || !rendererMatches) {
            throw new AccessCredentialDataAccessException(
                    "Access credential storage returned noncanonical metadata.", null);
        }
    }

    private static void ensureArtifactMatches(
            AccessCredentialStoredDocument metadata,
            AccessCredentialDocument document) {
        if (document == null
                || !CONTENT_TYPE.equals(document.contentType())
                || document.sizeBytes() != metadata.sizeBytes()
                || !checksum(document.bytes()).equals(metadata.checksumSha256())) {
            throw new AccessCredentialDataAccessException(
                    "Stored access credential does not match its metadata.", null);
        }
    }

    private UUID authorizeClientBranch(
            UUID clientId,
            AuthenticatedActor actor) {
        if (branchContextResolver == null) {
            return null;
        }
        ClientAccessDetails client = clientQuery.findById(clientId)
                .orElseThrow(() -> new AccessCredentialClientNotFoundException(clientId));
        if (!clientId.equals(client.id())) {
            throw new AccessCredentialDataAccessException(
                    "Client access projection does not match the requested client.", null);
        }
        return authorizeClientBranch(client, actor);
    }

    private UUID authorizeClientBranch(
            ClientAccessDetails client,
            AuthenticatedActor actor) {
        if (branchContextResolver == null || client.homeBranchId() == null) {
            return client.homeBranchId();
        }
        BranchOperationContext context = branchContextResolver.resolveOperation(actor.id());
        BranchOwnedResourceReference resource = new BranchOwnedResourceReference(
                client.id(),
                context.organizationId(),
                client.homeBranchId());
        if (context.organizationWide()) {
            BranchResourceAuthorizationPolicy.requireOrganizationResourceAccess(
                    context,
                    resource);
        } else {
            BranchResourceAuthorizationPolicy.requireActiveResourceAccess(
                    context,
                    resource);
        }
        return client.homeBranchId();
    }

    private static void requireHistory(AccessCredentialHistoryDetails history) {
        if (history == null) {
            throw new AccessCredentialDataAccessException(
                    "Access credential history could not be persisted.", null);
        }
    }

    private Instant serverNow() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static String credentialCode(UUID credentialId) {
        return "AC-" + credentialId.toString().replace("-", "")
                .substring(0, 24).toUpperCase(Locale.ROOT);
    }

    private static String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static void requireActor(AuthenticatedActor actor) {
        if (actor == null || actor.id() == null
                || actor.username() == null || actor.username().isBlank()) {
            throw new AccessCredentialValidationException("Authenticated actor is required.");
        }
    }

    private static void requireIdentifier(UUID value, String label) {
        if (value == null) {
            throw new AccessCredentialValidationException(label + " is required.");
        }
    }

    private record PreparedArtifact(
            AccessCredentialStoredDocument documentMetadata,
            String storageKey,
            String fingerprint,
            String payloadVersion,
            String tokenSchemeVersion) {
    }
}
