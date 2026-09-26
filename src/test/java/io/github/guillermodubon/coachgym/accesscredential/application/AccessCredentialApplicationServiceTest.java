package io.github.guillermodubon.coachgym.accesscredential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDetails;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDocument;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialIssued;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialReplaced;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialRevoked;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialTokenProtector;
import io.github.guillermodubon.coachgym.client.ClientAccessDetails;
import io.github.guillermodubon.coachgym.client.ClientAccessQuery;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.BranchOperationContext;
import io.github.guillermodubon.coachgym.user.BranchOperationContextResolver;
import io.github.guillermodubon.coachgym.user.BranchResourceAuthorizationException;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AccessCredentialApplicationServiceTest {

    private static final UUID CLIENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000005001");
    private static final UUID ACTOR_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000005002");
    private static final UUID CREDENTIAL_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000005003");
    private static final UUID REPLACEMENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000005004");
    private static final Instant NOW = Instant.parse("2026-09-13T10:00:00.123456Z");
    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(ACTOR_ID, "receptionist");
    private static final byte[] PNG = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02, 0x03
    };

    @Mock private AccessCredentialStore credentialStore;
    @Mock private AccessCredentialQuery credentialQuery;
    @Mock private AccessCredentialHistoryStore historyStore;
    @Mock private AccessCredentialHistoryQuery historyQuery;
    @Mock private ClientAccessQuery clientQuery;
    @Mock private AccessCredentialTokenGenerator tokenGenerator;
    @Mock private AccessCredentialTokenProtector tokenProtector;
    @Mock private AccessCredentialQrRenderer qrRenderer;
    @Mock private AccessCredentialStorage storage;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private BranchOperationContextResolver branchContextResolver;

    private AccessCredentialApplicationService service;
    private AccessCredentialDocument document;
    private AccessCredentialStoredDocument storedDocument;

    @BeforeEach
    void setUp() {
        service = new AccessCredentialApplicationService(
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
                Clock.fixed(NOW, ZoneOffset.UTC));
        document = new AccessCredentialDocument("image/png", PNG);
        storedDocument = new AccessCredentialStoredDocument(
                "access-credentials/00000000-0000-0000-0000-000000000000.png",
                "image/png",
                PNG.length,
                checksum(PNG),
                "qr-zxing-v1");
    }

    @Test
    void issuePersistsProtectedMetadataHistoryAndOnePublicEvent() {
        given(clientQuery.findById(CLIENT_ID)).willReturn(Optional.of(activeClient()));
        given(credentialStore.findActiveByClientIdForUpdate(CLIENT_ID))
                .willReturn(Optional.empty());
        given(tokenGenerator.generate()).willReturn(token());
        given(tokenProtector.fingerprint(any())).willReturn("a".repeat(64));
        given(tokenProtector.schemeVersion()).willReturn("sha256-v1");
        given(qrRenderer.render(any())).willReturn(document);
        given(qrRenderer.rendererVersion()).willReturn("qr-zxing-v1");
        given(storage.generateStorageKey(any())).willAnswer(invocation ->
                "access-credentials/" + invocation.getArgument(0) + ".png");
        given(storage.store(any(), any(), any())).willAnswer(invocation ->
                new AccessCredentialStoredDocument(
                        invocation.getArgument(0),
                        "image/png",
                        PNG.length,
                        checksum(PNG),
                        "qr-zxing-v1"));
        given(credentialStore.insert(any())).willAnswer(invocation -> {
            AccessCredentialPersistenceCommand command = invocation.getArgument(0);
            return activeDetails(
                    command.id(), command.clientId(), command.credentialCode(),
                    command.issuedAt(), command.issuedByUserId(), 0);
        });
        given(historyStore.append(any())).willReturn(initialHistory(CREDENTIAL_ID));

        AccessCredentialDetails result = service.issue(
                new IssueAccessCredentialCommand(CLIENT_ID), ACTOR);

        assertThat(result.status()).isEqualTo(AccessCredentialStatus.ACTIVE);
        assertThat(result.issuedAt()).isEqualTo(NOW);
        ArgumentCaptor<AccessCredentialPersistenceCommand> command =
                ArgumentCaptor.forClass(AccessCredentialPersistenceCommand.class);
        verify(credentialStore).insert(command.capture());
        assertThat(command.getValue().tokenFingerprint()).isEqualTo("a".repeat(64));
        assertThat(command.getValue().toString()).doesNotContain("a".repeat(64));
        ArgumentCaptor<AccessCredentialIssued> event =
                ArgumentCaptor.forClass(AccessCredentialIssued.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().credentialId()).isEqualTo(result.id());
        assertThat(event.getValue().actorIdentifier()).isEqualTo(ACTOR.username());
        assertThat(event.getValue().toString()).doesNotContain(token());
        verify(historyStore).append(any());
    }

    @Test
    void issueReturnsTheExistingActiveCredentialWithoutGeneratingAnotherArtifact() {
        AccessCredentialDetails existing = activeDetails(
                CREDENTIAL_ID, CLIENT_ID, "AC-EXISTING", NOW, ACTOR_ID, 2);
        given(clientQuery.findById(CLIENT_ID)).willReturn(Optional.of(activeClient()));
        given(credentialStore.findActiveByClientIdForUpdate(CLIENT_ID))
                .willReturn(Optional.of(existing));

        assertThat(service.issue(new IssueAccessCredentialCommand(CLIENT_ID), ACTOR))
                .isSameAs(existing);

        verifyNoInteractions(tokenGenerator, tokenProtector, qrRenderer, storage,
                historyStore, eventPublisher);
    }

    @Test
    void issueRejectsAnInactiveClientBeforeArtifactWork() {
        given(clientQuery.findById(CLIENT_ID)).willReturn(Optional.of(
                new ClientAccessDetails(CLIENT_ID, "CLI-000001", ClientStatus.INACTIVE)));

        assertThatThrownBy(() -> service.issue(
                new IssueAccessCredentialCommand(CLIENT_ID), ACTOR))
                .isInstanceOf(AccessCredentialEligibilityException.class);

        verifyNoInteractions(tokenGenerator, tokenProtector, qrRenderer, storage,
                historyStore, eventPublisher);
    }

    @Test
    void rendererFailureDoesNotPersistOrPublish() {
        given(clientQuery.findById(CLIENT_ID)).willReturn(Optional.of(activeClient()));
        given(credentialStore.findActiveByClientIdForUpdate(CLIENT_ID))
                .willReturn(Optional.empty());
        given(tokenGenerator.generate()).willReturn(token());
        given(tokenProtector.fingerprint(any())).willReturn("a".repeat(64));
        given(qrRenderer.render(any())).willThrow(
                new AccessCredentialRenderException("render failed", null));

        assertThatThrownBy(() -> service.issue(
                new IssueAccessCredentialCommand(CLIENT_ID), ACTOR))
                .isInstanceOf(AccessCredentialRenderException.class);

        verifyNoInteractions(storage, historyStore, eventPublisher);
    }

    @Test
    void storageFailureAttemptsCompensationAndDoesNotPersist() {
        prepareIssueUntilStorage();
        doThrow(new AccessCredentialStorageException("storage failed"))
                .when(storage).store(any(), any(), any());

        assertThatThrownBy(() -> service.issue(
                new IssueAccessCredentialCommand(CLIENT_ID), ACTOR))
                .isInstanceOf(AccessCredentialStorageException.class);

        verify(storage).delete(any());
        verify(credentialStore, never()).insert(any());
        verifyNoInteractions(historyStore, eventPublisher);
    }

    @Test
    void persistenceFailureDeletesStagedArtifact() {
        prepareIssueUntilStorage();
        given(storage.store(any(), any(), any())).willAnswer(invocation ->
                storedDocumentFor(invocation.getArgument(0)));
        given(credentialStore.insert(any())).willThrow(
                new AccessCredentialDataAccessException("database failed", null));

        assertThatThrownBy(() -> service.issue(
                new IssueAccessCredentialCommand(CLIENT_ID), ACTOR))
                .isInstanceOf(AccessCredentialDataAccessException.class);

        verify(storage).delete(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void compensationFailureIsReportedWithoutLeakingTheStorageKey() {
        prepareIssueUntilStorage();
        doThrow(new AccessCredentialStorageException("storage failed"))
                .when(storage).store(any(), any(), any());
        doThrow(new AccessCredentialStorageException("cleanup failed"))
                .when(storage).delete(any());

        assertThatThrownBy(() -> service.issue(
                new IssueAccessCredentialCommand(CLIENT_ID), ACTOR))
                .isInstanceOf(AccessCredentialStorageException.class)
                .hasMessage("Access credential artifact cleanup failed.")
                .hasMessageNotContaining("access-credentials/");

        verifyNoInteractions(historyStore, eventPublisher);
    }

    @Test
    void revokePersistsHistoryAndPublishesOnlyAfterCanonicalTransition() {
        AccessCredentialDetails current = activeDetails(
                CREDENTIAL_ID, CLIENT_ID, "AC-REVOKE", NOW, ACTOR_ID, 3);
        AccessCredentialDetails revoked = revokedDetails(
                CREDENTIAL_ID, CLIENT_ID, "AC-REVOKE", NOW, ACTOR_ID, NOW, ACTOR_ID, 4, null);
        given(credentialQuery.findById(CREDENTIAL_ID)).willReturn(Optional.of(current));
        given(credentialStore.findByIdForUpdate(CREDENTIAL_ID)).willReturn(Optional.of(current));
        given(credentialStore.revoke(
                CREDENTIAL_ID, "lost card", ACTOR_ID, NOW, 3)).willReturn(revoked);
        given(historyStore.append(any())).willReturn(revokedHistory(CREDENTIAL_ID, CLIENT_ID));

        AccessCredentialDetails result = service.revoke(
                new RevokeAccessCredentialCommand(CREDENTIAL_ID, " lost card ", 3), ACTOR);

        assertThat(result.status()).isEqualTo(AccessCredentialStatus.REVOKED);
        verify(historyStore).append(any());
        ArgumentCaptor<AccessCredentialRevoked> event =
                ArgumentCaptor.forClass(AccessCredentialRevoked.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().reasonPresent()).isTrue();
    }

    @Test
    void clientScopedRevokeLocksClientBeforeResolvingCurrentCredential() {
        AccessCredentialDetails current = activeDetails(
                CREDENTIAL_ID, CLIENT_ID, "AC-REVOKE", NOW, ACTOR_ID, 3);
        AccessCredentialDetails revoked = revokedDetails(
                CREDENTIAL_ID, CLIENT_ID, "AC-REVOKE", NOW, ACTOR_ID, NOW, ACTOR_ID, 4, null);
        given(credentialStore.findActiveByClientIdForUpdate(CLIENT_ID))
                .willReturn(Optional.of(current));
        given(credentialStore.revoke(
                CREDENTIAL_ID, "lost card", ACTOR_ID, NOW, 3)).willReturn(revoked);
        given(historyStore.append(any())).willReturn(revokedHistory(CREDENTIAL_ID, CLIENT_ID));

        AccessCredentialDetails result = service.revoke(
                new RevokeClientAccessCredentialCommand(CLIENT_ID, " lost card ", 3), ACTOR);

        assertThat(result.status()).isEqualTo(AccessCredentialStatus.REVOKED);
        verify(credentialStore).lockClientForLifecycle(CLIENT_ID);
        verify(credentialStore).findActiveByClientIdForUpdate(CLIENT_ID);
    }

    @Test
    void clientScopedRevokeReportsStateConflictWhenTheLatestCredentialIsFinal() {
        AccessCredentialDetails revoked = revokedDetails(
                CREDENTIAL_ID, CLIENT_ID, "AC-REVOKED", NOW, ACTOR_ID, NOW, ACTOR_ID, 4, null);
        given(credentialStore.findActiveByClientIdForUpdate(CLIENT_ID))
                .willReturn(Optional.empty());
        given(credentialStore.findLatestByClientIdForUpdate(CLIENT_ID))
                .willReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.revoke(
                new RevokeClientAccessCredentialCommand(CLIENT_ID, "again", 4), ACTOR))
                .isInstanceOf(AccessCredentialStateConflictException.class);

        verify(credentialStore).lockClientForLifecycle(CLIENT_ID);
        verify(credentialStore, never()).revoke(any(), any(), any(), any(), anyLong());
        verifyNoInteractions(historyStore, eventPublisher);
    }

    @Test
    void repeatedRevokeIsRejectedWithoutAnotherHistoryEntry() {
        AccessCredentialDetails revoked = revokedDetails(
                CREDENTIAL_ID, CLIENT_ID, "AC-REVOKED", NOW, ACTOR_ID, NOW, ACTOR_ID, 4, null);
        given(credentialQuery.findById(CREDENTIAL_ID)).willReturn(Optional.of(revoked));
        given(credentialStore.findByIdForUpdate(CREDENTIAL_ID)).willReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.revoke(
                new RevokeAccessCredentialCommand(CREDENTIAL_ID, "again", 4), ACTOR))
                .isInstanceOf(AccessCredentialStateConflictException.class);

        verify(credentialStore, never()).revoke(any(), any(), any(), any(), anyLong());
        verifyNoInteractions(historyStore, eventPublisher);
    }

    @Test
    void replacementRevokesOldCredentialCreatesNewOneAndPublishesOneEvent() {
        AccessCredentialDetails current = activeDetails(
                CREDENTIAL_ID, CLIENT_ID, "AC-OLD", NOW, ACTOR_ID, 2);
        AccessCredentialDetails revoked = revokedDetails(
                CREDENTIAL_ID, CLIENT_ID, "AC-OLD", NOW, ACTOR_ID, NOW, ACTOR_ID, 3, null);
        given(credentialQuery.findById(CREDENTIAL_ID)).willReturn(Optional.of(current));
        given(credentialStore.findByIdForUpdate(CREDENTIAL_ID)).willReturn(Optional.of(current));
        given(credentialStore.revoke(
                CREDENTIAL_ID, "lost card", ACTOR_ID, NOW, 2)).willReturn(revoked);
        given(credentialStore.insert(any())).willAnswer(invocation -> {
            AccessCredentialPersistenceCommand command = invocation.getArgument(0);
            return activeDetails(
                    command.id(), command.clientId(), command.credentialCode(),
                    command.issuedAt(), command.issuedByUserId(), 0);
        });
        given(credentialStore.attachReplacement(any(), any(), anyLong()))
                .willAnswer(invocation -> revokedDetails(
                        CREDENTIAL_ID, CLIENT_ID, "AC-OLD", NOW, ACTOR_ID, NOW, ACTOR_ID, 4,
                        invocation.getArgument(1)));
        given(historyStore.append(any())).willReturn(initialHistory(REPLACEMENT_ID));
        prepareArtifact();

        AccessCredentialDetails result = service.replace(
                new ReplaceAccessCredentialCommand(CREDENTIAL_ID, " lost card ", 2), ACTOR);

        assertThat(result.status()).isEqualTo(AccessCredentialStatus.ACTIVE);
        verify(credentialStore).revoke(CREDENTIAL_ID, "lost card", ACTOR_ID, NOW, 2);
        verify(credentialStore).attachReplacement(eq(CREDENTIAL_ID), any(), eq(3L));
        verify(historyStore, org.mockito.Mockito.times(2)).append(any());
        ArgumentCaptor<AccessCredentialReplaced> event =
                ArgumentCaptor.forClass(AccessCredentialReplaced.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().previousCredentialId()).isEqualTo(CREDENTIAL_ID);
        assertThat(event.getValue().replacementCredentialId()).isEqualTo(result.id());
    }

    @Test
    void replacementFailureCompensatesTheNewArtifact() {
        AccessCredentialDetails current = activeDetails(
                CREDENTIAL_ID, CLIENT_ID, "AC-OLD", NOW, ACTOR_ID, 2);
        AccessCredentialDetails revoked = revokedDetails(
                CREDENTIAL_ID, CLIENT_ID, "AC-OLD", NOW, ACTOR_ID, NOW, ACTOR_ID, 3, null);
        given(credentialQuery.findById(CREDENTIAL_ID)).willReturn(Optional.of(current));
        given(credentialStore.findByIdForUpdate(CREDENTIAL_ID)).willReturn(Optional.of(current));
        given(credentialStore.revoke(any(), any(), any(), any(), any(Long.class)))
                .willReturn(revoked);
        given(historyStore.append(any())).willReturn(revokedHistory(CREDENTIAL_ID, CLIENT_ID));
        given(credentialStore.insert(any())).willThrow(
                new AccessCredentialDataAccessException("database failed", null));
        prepareArtifact();

        assertThatThrownBy(() -> service.replace(
                new ReplaceAccessCredentialCommand(CREDENTIAL_ID, "lost card", 2), ACTOR))
                .isInstanceOf(AccessCredentialDataAccessException.class);

        verify(storage).delete(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void issuePublishesOnlyAfterCommit() {
        prepareIssueSuccess();
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.issue(new IssueAccessCredentialCommand(CLIENT_ID), ACTOR);

            verify(eventPublisher, never()).publishEvent(any());
            TransactionSynchronization synchronization = TransactionSynchronizationManager
                    .getSynchronizations().get(0);
            synchronization.afterCommit();
            verify(eventPublisher).publishEvent(any(AccessCredentialIssued.class));
            synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            verify(storage, never()).delete(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void issueRollbackCleansStagedArtifactWithoutPublishingAnEvent() {
        prepareIssueSuccess();
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.issue(new IssueAccessCredentialCommand(CLIENT_ID), ACTOR);

            TransactionSynchronization synchronization = TransactionSynchronizationManager
                    .getSynchronizations().get(0);
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

            verify(storage).delete(any());
            verify(eventPublisher, never()).publishEvent(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void downloadChecksCanonicalStoredMetadataBeforeReturningBytes() {
        AccessCredentialDetails details = activeDetails(
                CREDENTIAL_ID, CLIENT_ID, "AC-DOWNLOAD", NOW, ACTOR_ID, 0);
        AccessCredentialStoredDocument metadata = storedDocumentFor(
                "access-credentials/" + CREDENTIAL_ID + ".png");
        given(credentialQuery.findActiveByClientId(CLIENT_ID)).willReturn(Optional.of(details));
        given(credentialQuery.findArtifactByCredentialId(CREDENTIAL_ID))
                .willReturn(Optional.of(metadata));
        given(storage.load(
                metadata.storageKey(), metadata.contentType(), metadata.sizeBytes(),
                metadata.checksumSha256())).willReturn(document);

        AccessCredentialContent content = service.downloadActiveByClientId(CLIENT_ID);

        assertThat(content.details()).isSameAs(details);
        assertThat(content.document()).isSameAs(document);
    }

    @Test
    void downloadRejectsBytesThatDoNotMatchPersistedChecksum() {
        AccessCredentialDetails details = activeDetails(
                CREDENTIAL_ID, CLIENT_ID, "AC-DOWNLOAD", NOW, ACTOR_ID, 0);
        AccessCredentialStoredDocument metadata = storedDocumentFor(
                "access-credentials/" + CREDENTIAL_ID + ".png");
        AccessCredentialDocument different = new AccessCredentialDocument(
                "image/png", new byte[] {
                    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x09
                });
        given(credentialQuery.findActiveByClientId(CLIENT_ID)).willReturn(Optional.of(details));
        given(credentialQuery.findArtifactByCredentialId(CREDENTIAL_ID))
                .willReturn(Optional.of(metadata));
        given(storage.load(any(), any(), anyLong(), any())).willReturn(different);

        assertThatThrownBy(() -> service.downloadActiveByClientId(CLIENT_ID))
                .isInstanceOf(AccessCredentialDataAccessException.class)
                .hasMessage("Stored access credential does not match its metadata.");
    }

    @Test
    void branchScopedDownloadRejectsCrossBranchClientBeforeReadingArtifactOrStorage() {
        UUID activeBranchId = UUID.randomUUID();
        UUID clientHomeBranchId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        given(clientQuery.findById(CLIENT_ID)).willReturn(Optional.of(
                new ClientAccessDetails(
                        CLIENT_ID, "CLI-000001", ClientStatus.ACTIVE, clientHomeBranchId)));
        given(branchContextResolver.resolveOperation(ACTOR_ID)).willReturn(
                new BranchOperationContext(
                        ACTOR_ID, organizationId, StaffScopeType.BRANCH,
                        activeBranchId, Set.of(activeBranchId)));

        assertThatThrownBy(() -> branchAwareService()
                .downloadActiveByClientId(CLIENT_ID, ACTOR))
                .isInstanceOf(BranchResourceAuthorizationException.class);

        verifyNoInteractions(credentialQuery, storage);
    }

    @Test
    void branchAwareServiceRejectsLegacyActorlessDownloadBeforeAnyLookup() {
        assertThatThrownBy(() -> branchAwareService()
                .downloadActiveByClientId(CLIENT_ID))
                .isInstanceOf(BranchResourceAuthorizationException.class);

        verifyNoInteractions(clientQuery, credentialQuery, storage);
    }

    @Test
    void lifecycleMethodsDeclareTransactionsAndTheBlueprintRoleMatrix() throws Exception {
        var issue = AccessCredentialApplicationService.class.getMethod(
                "issue", IssueAccessCredentialCommand.class, AuthenticatedActor.class);
        var revoke = AccessCredentialApplicationService.class.getMethod(
                "revoke", RevokeAccessCredentialCommand.class, AuthenticatedActor.class);
        var replace = AccessCredentialApplicationService.class.getMethod(
                "replace", ReplaceAccessCredentialCommand.class, AuthenticatedActor.class);
        var download = AccessCredentialApplicationService.class.getMethod(
                "downloadActiveByClientId", UUID.class);

        assertThat(issue.getAnnotation(Transactional.class)).isNotNull();
        assertThat(revoke.getAnnotation(Transactional.class)).isNotNull();
        assertThat(replace.getAnnotation(Transactional.class)).isNotNull();
        assertThat(download.getAnnotation(Transactional.class)).isNull();
        assertThat(issue.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAnyRole('ADMIN', 'RECEPTIONIST')");
        assertThat(revoke.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAnyRole('ADMIN', 'RECEPTIONIST')");
        assertThat(replace.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAnyRole('ADMIN', 'RECEPTIONIST')");
    }

    @Test
    void publicLifecycleEventsExposeOnlyPrivacySafeFields() {
        AccessCredentialIssued issued = new AccessCredentialIssued(
                CREDENTIAL_ID, CLIENT_ID, "AC-SAFE", "v1", "sha256-v1",
                ACTOR_ID, ACTOR.username(), NOW);
        AccessCredentialRevoked revoked = new AccessCredentialRevoked(
                CREDENTIAL_ID, CLIENT_ID, "AC-SAFE", AccessCredentialStatus.ACTIVE,
                AccessCredentialStatus.REVOKED, ACTOR_ID, ACTOR.username(), NOW, true);
        AccessCredentialReplaced replaced = new AccessCredentialReplaced(
                CREDENTIAL_ID, REPLACEMENT_ID, CLIENT_ID, AccessCredentialStatus.REVOKED,
                AccessCredentialStatus.ACTIVE, ACTOR_ID, ACTOR.username(), NOW, true);

        assertThat(issued.toString()).doesNotContain(token(), "a".repeat(64));
        assertThat(revoked.toString()).doesNotContain("reason=");
        assertThat(replaced.toString()).doesNotContain("storage", "digest", "token", "bytes");
        for (Class<?> eventType : new Class<?>[] {
            AccessCredentialIssued.class,
            AccessCredentialRevoked.class,
            AccessCredentialReplaced.class
        }) {
            assertThat(eventType.getDeclaredFields())
                    .extracting(java.lang.reflect.Field::getName)
                    .noneMatch(name -> name.matches(
                            "(?i).*(raw.*token|fingerprint|digest|hmac|storage|bytes|secret).*"));
        }
    }

    private void prepareIssueUntilStorage() {
        given(clientQuery.findById(CLIENT_ID)).willReturn(Optional.of(activeClient()));
        given(credentialStore.findActiveByClientIdForUpdate(CLIENT_ID))
                .willReturn(Optional.empty());
        given(tokenGenerator.generate()).willReturn(token());
        given(tokenProtector.fingerprint(any())).willReturn("a".repeat(64));
        given(tokenProtector.schemeVersion()).willReturn("sha256-v1");
        given(qrRenderer.render(any())).willReturn(document);
        given(qrRenderer.rendererVersion()).willReturn("qr-zxing-v1");
        given(storage.generateStorageKey(any())).willAnswer(invocation ->
                "access-credentials/" + invocation.getArgument(0) + ".png");
    }

    private void prepareIssueSuccess() {
        prepareIssueUntilStorage();
        given(storage.store(any(), any(), any())).willAnswer(invocation ->
                storedDocumentFor(invocation.getArgument(0)));
        given(credentialStore.insert(any())).willAnswer(invocation -> {
            AccessCredentialPersistenceCommand command = invocation.getArgument(0);
            return activeDetails(
                    command.id(), command.clientId(), command.credentialCode(),
                    command.issuedAt(), command.issuedByUserId(), 0);
        });
        given(historyStore.append(any())).willReturn(initialHistory(CREDENTIAL_ID));
    }

    private void prepareArtifact() {
        given(tokenGenerator.generate()).willReturn(token());
        given(tokenProtector.fingerprint(any())).willReturn("a".repeat(64));
        given(tokenProtector.schemeVersion()).willReturn("sha256-v1");
        given(qrRenderer.render(any())).willReturn(document);
        given(qrRenderer.rendererVersion()).willReturn("qr-zxing-v1");
        given(storage.generateStorageKey(any())).willAnswer(invocation ->
                "access-credentials/" + invocation.getArgument(0) + ".png");
        given(storage.store(any(), any(), any())).willAnswer(invocation ->
                storedDocumentFor(invocation.getArgument(0)));
    }

    private static ClientAccessDetails activeClient() {
        return new ClientAccessDetails(CLIENT_ID, "CLI-000001", ClientStatus.ACTIVE);
    }

    private AccessCredentialApplicationService branchAwareService() {
        return new AccessCredentialApplicationService(
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
                Clock.fixed(NOW, ZoneOffset.UTC),
                branchContextResolver);
    }

    private static AccessCredentialStoredDocument storedDocumentFor(String storageKey) {
        return new AccessCredentialStoredDocument(
                storageKey, "image/png", PNG.length, checksum(PNG), "qr-zxing-v1");
    }

    private static AccessCredentialDetails activeDetails(
            UUID id,
            UUID clientId,
            String code,
            Instant issuedAt,
            UUID issuedBy,
            long version) {
        return new AccessCredentialDetails(
                id, clientId, code, AccessCredentialStatus.ACTIVE, "v1", issuedAt, issuedBy,
                null, null, null, version);
    }

    private static AccessCredentialDetails revokedDetails(
            UUID id,
            UUID clientId,
            String code,
            Instant issuedAt,
            UUID issuedBy,
            Instant revokedAt,
            UUID revokedBy,
            long version,
            UUID replacementId) {
        return new AccessCredentialDetails(
                id, clientId, code, AccessCredentialStatus.REVOKED, "v1", issuedAt, issuedBy,
                revokedAt, revokedBy, replacementId, version);
    }

    private static io.github.guillermodubon.coachgym.accesscredential.AccessCredentialHistoryDetails
            initialHistory(UUID credentialId) {
        return new io.github.guillermodubon.coachgym.accesscredential.AccessCredentialHistoryDetails(
                UUID.randomUUID(), credentialId, CLIENT_ID, null, AccessCredentialStatus.ACTIVE,
                null, NOW, ACTOR_ID, null);
    }

    private static io.github.guillermodubon.coachgym.accesscredential.AccessCredentialHistoryDetails
            revokedHistory(UUID credentialId, UUID clientId) {
        return new io.github.guillermodubon.coachgym.accesscredential.AccessCredentialHistoryDetails(
                UUID.randomUUID(), credentialId, clientId, AccessCredentialStatus.ACTIVE,
                AccessCredentialStatus.REVOKED, "lost card", NOW, ACTOR_ID, null);
    }

    private static String token() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
            0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
            0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
            0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20
        });
    }

    private static String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
