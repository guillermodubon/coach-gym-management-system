package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDetails;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDocument;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptGenerated;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptOrganization;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptSnapshot;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptSourceSnapshot;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.BranchOperationContext;
import io.github.guillermodubon.coachgym.user.BranchOperationContextResolver;
import io.github.guillermodubon.coachgym.user.BranchResourceAuthorizationException;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentReceiptApplicationServiceTest {

    private static final UUID PAYMENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000001001");
    private static final UUID ACTOR_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000001002");
    private static final Instant PAID_AT = Instant.parse("2026-09-11T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-11T10:05:00Z");
    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(ACTOR_ID, "receptionist");

    @Mock private PaymentReceiptStore receiptStore;
    @Mock private PaymentReceiptQuery receiptQuery;
    @Mock private PaymentReceiptSnapshotQuery snapshotQuery;
    @Mock private PaymentReceiptOrganizationQuery organizationQuery;
    @Mock private PaymentReceiptRenderer renderer;
    @Mock private PaymentReceiptStorage storage;
    @Mock private PaymentReceiptNumberGenerator numberGenerator;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private BranchOperationContextResolver branchContextResolver;

    private PaymentReceiptApplicationService service;
    private PaymentReceiptDocument document;

    @BeforeEach
    void setUp() {
        service = new PaymentReceiptApplicationService(
                receiptStore, receiptQuery, snapshotQuery, organizationQuery,
                renderer, storage, numberGenerator, eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        document = PaymentReceiptDocument.fromPdfBytes(
                "%PDF-1.7\nreceipt".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void generatesCanonicalReceiptWithAuthoritativeSnapshotAndServerClock() {
        PaymentReceiptSourceSnapshot source = source(PaymentStatus.PAID, PaymentMethod.CASH);
        PaymentReceiptOrganization organization = organization();
        given(receiptQuery.findByPaymentId(PAYMENT_ID)).willReturn(Optional.empty());
        given(snapshotQuery.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(source));
        given(numberGenerator.next()).willReturn("REC-00000000000000000000000001");
        given(organizationQuery.findCurrent()).willReturn(organization);
        given(renderer.render(any(PaymentReceiptSnapshot.class), any()))
                .willReturn(document);
        given(storage.generateStorageKey(any())).willReturn("receipts/generated.pdf");
        given(receiptStore.save(any(PaymentReceiptDetails.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        PaymentReceiptDetails result = service.generate(
                new GeneratePaymentReceiptCommand(PAYMENT_ID), ACTOR);

        assertThat(result.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(result.generatedAt()).isEqualTo(NOW);
        assertThat(result.generatedByUserId()).isEqualTo(ACTOR_ID);
        assertThat(result.generatedByDisplayName()).isEqualTo("receptionist");
        assertThat(result.testMode()).isFalse();
        assertThat(result.checksumSha256()).isEqualTo(document.checksumSha256());

        InOrder order = inOrder(receiptQuery, snapshotQuery, organizationQuery,
                renderer, storage, receiptStore, eventPublisher);
        order.verify(receiptQuery).findByPaymentId(PAYMENT_ID);
        order.verify(snapshotQuery).findByPaymentId(PAYMENT_ID);
        order.verify(organizationQuery).findCurrent();
        order.verify(renderer).render(any(PaymentReceiptSnapshot.class), any());
        order.verify(storage).store("receipts/generated.pdf", document);
        order.verify(receiptStore).save(any(PaymentReceiptDetails.class));
        order.verify(eventPublisher).publishEvent(any(PaymentReceiptGenerated.class));
    }

    @Test
    void marksStripeCardReceiptAsTestModeWithoutExposingProviderData() {
        PaymentReceiptSourceSnapshot source = source(PaymentStatus.PAID, PaymentMethod.CARD);
        given(receiptQuery.findByPaymentId(PAYMENT_ID)).willReturn(Optional.empty());
        given(snapshotQuery.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(source));
        given(numberGenerator.next()).willReturn("REC-00000000000000000000000002");
        given(organizationQuery.findCurrent()).willReturn(organization());
        given(renderer.render(any(), any())).willReturn(document);
        given(storage.generateStorageKey(any())).willReturn("receipts/card.pdf");
        given(receiptStore.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        PaymentReceiptDetails result = service.generate(
                new GeneratePaymentReceiptCommand(PAYMENT_ID), ACTOR);

        assertThat(result.testMode()).isTrue();
    }

    @Test
    void returnsExistingCanonicalReceiptWithoutRepeatingGenerationOrEvent() {
        PaymentReceiptDetails existing = details(
                UUID.fromString("00000000-0000-0000-0000-000000001003"), false);
        given(receiptQuery.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(existing));

        assertThat(service.generate(new GeneratePaymentReceiptCommand(PAYMENT_ID), ACTOR))
                .isSameAs(existing);

        verifyNoInteractions(snapshotQuery, organizationQuery, renderer, storage,
                receiptStore, numberGenerator, eventPublisher);
    }

    @Test
    void rejectsIneligiblePaymentBeforeRenderingOrStorage() {
        given(receiptQuery.findByPaymentId(PAYMENT_ID)).willReturn(Optional.empty());
        given(snapshotQuery.findByPaymentId(PAYMENT_ID))
                .willReturn(Optional.of(source(PaymentStatus.REFUNDED, PaymentMethod.CASH)));

        assertThatThrownBy(() -> service.generate(
                new GeneratePaymentReceiptCommand(PAYMENT_ID), ACTOR))
                .isInstanceOf(PaymentReceiptStateConflictException.class);

        verifyNoInteractions(organizationQuery, renderer, storage, receiptStore,
                numberGenerator, eventPublisher);
    }

    @Test
    void rendererFailureLeavesPersistenceAndStorageUntouched() {
        given(receiptQuery.findByPaymentId(PAYMENT_ID)).willReturn(Optional.empty());
        given(snapshotQuery.findByPaymentId(PAYMENT_ID))
                .willReturn(Optional.of(source(PaymentStatus.PAID, PaymentMethod.CASH)));
        given(numberGenerator.next()).willReturn("REC-00000000000000000000000003");
        given(organizationQuery.findCurrent()).willReturn(organization());
        given(renderer.render(any(), any())).willThrow(
                new PaymentReceiptRenderException("render failed", null));

        assertThatThrownBy(() -> service.generate(
                new GeneratePaymentReceiptCommand(PAYMENT_ID), ACTOR))
                .isInstanceOf(PaymentReceiptRenderException.class);

        verifyNoInteractions(storage, receiptStore, eventPublisher);
    }

    @Test
    void storageFailureIsCompensatedAndDoesNotPersistMetadata() {
        given(receiptQuery.findByPaymentId(PAYMENT_ID)).willReturn(Optional.empty());
        given(snapshotQuery.findByPaymentId(PAYMENT_ID))
                .willReturn(Optional.of(source(PaymentStatus.PAID, PaymentMethod.CASH)));
        given(numberGenerator.next()).willReturn("REC-00000000000000000000000004");
        given(organizationQuery.findCurrent()).willReturn(organization());
        given(renderer.render(any(), any())).willReturn(document);
        given(storage.generateStorageKey(any())).willReturn("receipts/storage-failure.pdf");
        doThrow(new PaymentReceiptStorageException("storage failed"))
                .when(storage).store("receipts/storage-failure.pdf", document);

        assertThatThrownBy(() -> service.generate(
                new GeneratePaymentReceiptCommand(PAYMENT_ID), ACTOR))
                .isInstanceOf(PaymentReceiptStorageException.class);

        verify(storage).delete("receipts/storage-failure.pdf");
        verifyNoInteractions(receiptStore, eventPublisher);
    }

    @Test
    void persistenceFailureCompensatesNonCanonicalArtifact() {
        given(receiptQuery.findByPaymentId(PAYMENT_ID)).willReturn(Optional.empty());
        given(snapshotQuery.findByPaymentId(PAYMENT_ID))
                .willReturn(Optional.of(source(PaymentStatus.PAID, PaymentMethod.CASH)));
        given(numberGenerator.next()).willReturn("REC-00000000000000000000000005");
        given(organizationQuery.findCurrent()).willReturn(organization());
        given(renderer.render(any(), any())).willReturn(document);
        given(storage.generateStorageKey(any())).willReturn("receipts/persistence-failure.pdf");
        given(receiptStore.save(any())).willThrow(
                new PaymentReceiptDataAccessException("persistence failed", null));

        assertThatThrownBy(() -> service.generate(
                new GeneratePaymentReceiptCommand(PAYMENT_ID), ACTOR))
                .isInstanceOf(PaymentReceiptDataAccessException.class);

        verify(storage).delete("receipts/persistence-failure.pdf");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void duplicateInsertDeletesLosingArtifactAndReturnsWinningReceipt() {
        PaymentReceiptDetails canonical = details(
                UUID.fromString("00000000-0000-0000-0000-000000001006"), false);
        given(receiptQuery.findByPaymentId(PAYMENT_ID))
                .willReturn(Optional.empty(), Optional.of(canonical));
        given(snapshotQuery.findByPaymentId(PAYMENT_ID))
                .willReturn(Optional.of(source(PaymentStatus.PAID, PaymentMethod.CASH)));
        given(numberGenerator.next()).willReturn("REC-00000000000000000000000006");
        given(organizationQuery.findCurrent()).willReturn(organization());
        given(renderer.render(any(), any())).willReturn(document);
        given(storage.generateStorageKey(any())).willReturn("receipts/loser.pdf");
        given(receiptStore.save(any())).willThrow(new PaymentReceiptDuplicateException(PAYMENT_ID));

        assertThat(service.generate(new GeneratePaymentReceiptCommand(PAYMENT_ID), ACTOR))
                .isSameAs(canonical);

        verify(storage).delete("receipts/loser.pdf");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void rejectsMissingActorBeforeReadingAnyPaymentData() {
        assertThatThrownBy(() -> service.generate(
                new GeneratePaymentReceiptCommand(PAYMENT_ID), null))
                .isInstanceOf(PaymentReceiptValidationException.class);

        verifyNoInteractions(receiptQuery, snapshotQuery, storage, receiptStore, eventPublisher);
    }

    @Test
    void downloadsCanonicalDocumentAfterValidatingPersistedMetadata() {
        PaymentReceiptDetails existing = details(
                UUID.fromString("00000000-0000-0000-0000-000000001007"), false);
        given(receiptQuery.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(existing));
        given(storage.load(
                PaymentReceiptStorageKey.forReceipt(existing.id()),
                existing.contentType(), existing.checksumSha256()))
                .willReturn(document);

        PaymentReceiptContent result = service.downloadByPaymentId(PAYMENT_ID);

        assertThat(result.details()).isSameAs(existing);
        assertThat(result.document()).isSameAs(document);
        verify(storage).load(
                PaymentReceiptStorageKey.forReceipt(existing.id()),
                existing.contentType(), existing.checksumSha256());
    }

    @Test
    void rejectsDocumentWhenStorageMetadataDoesNotMatchCanonicalReceipt() {
        PaymentReceiptDetails existing = details(
                UUID.fromString("00000000-0000-0000-0000-000000001008"), false);
        PaymentReceiptDocument differentDocument = PaymentReceiptDocument.fromPdfBytes(
                "%PDF-1.7\nother".getBytes(StandardCharsets.US_ASCII));
        given(receiptQuery.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(existing));
        given(storage.load(any(), any(), any())).willReturn(differentDocument);

        assertThatThrownBy(() -> service.downloadByPaymentId(PAYMENT_ID))
                .isInstanceOf(PaymentReceiptDataAccessException.class)
                .hasMessage("Stored payment receipt does not match its metadata.");
    }

    @Test
    void branchAwareServiceRejectsLegacyActorlessDownloadBeforeLookup() {
        assertThatThrownBy(() -> branchAwareService().downloadByPaymentId(PAYMENT_ID))
                .isInstanceOf(BranchResourceAuthorizationException.class);

        verifyNoInteractions(receiptQuery, storage);
    }

    @Test
    void branchScopedReceiptDownloadUsesBranchFilteredMetadataBeforeStorage() {
        UUID activeBranchId = UUID.randomUUID();
        given(branchContextResolver.resolveOperation(ACTOR_ID)).willReturn(
                new BranchOperationContext(
                        ACTOR_ID, UUID.randomUUID(), StaffScopeType.BRANCH,
                        activeBranchId, Set.of(activeBranchId)));

        assertThatThrownBy(() -> branchAwareService()
                .downloadByPaymentId(PAYMENT_ID, ACTOR))
                .isInstanceOf(PaymentReceiptNotFoundException.class);

        verify(receiptQuery).findByPaymentId(PAYMENT_ID, activeBranchId);
        verifyNoInteractions(storage);
    }

    private PaymentReceiptApplicationService branchAwareService() {
        return new PaymentReceiptApplicationService(
                receiptStore,
                receiptQuery,
                snapshotQuery,
                organizationQuery,
                renderer,
                storage,
                numberGenerator,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                branchContextResolver);
    }

    private static PaymentReceiptSourceSnapshot source(
            PaymentStatus status, PaymentMethod method) {
        return new PaymentReceiptSourceSnapshot(
                PAYMENT_ID, "PAY-000001", status, "CLI-000001", "Ana Martinez",
                "MEM-000001", "Premium", null, 1,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                new BigDecimal("30.00"), new BigDecimal("5.00"),
                new BigDecimal("25.00"), "USD", method, PAID_AT);
    }

    private static PaymentReceiptOrganization organization() {
        return new PaymentReceiptOrganization(
                "Coach Gym", "Coach Gym LLC", "hello@coachgym.test",
                "+50370000000", "San Salvador", "America/El_Salvador");
    }

    private static PaymentReceiptDetails details(UUID receiptId, boolean testMode) {
        PaymentReceiptDocument document = PaymentReceiptDocument.fromPdfBytes(
                "%PDF-1.7\nreceipt".getBytes(StandardCharsets.US_ASCII));
        return new PaymentReceiptDetails(
                receiptId, "REC-EXISTING", PAYMENT_ID, "PAY-000001", PaymentStatus.PAID,
                "CLI-000001", "Ana Martinez", "MEM-000001", "Premium", null, 1,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                new BigDecimal("30.00"), new BigDecimal("5.00"), new BigDecimal("25.00"),
                "USD", PaymentMethod.CASH, PAID_AT, NOW, ACTOR_ID, "receptionist",
                testMode, document.contentType(), document.sizeBytes(),
                document.checksumSha256(), "pdfbox-3.0.8", 0L);
    }
}
