package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDetails;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDocument;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptGenerated;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptOrganization;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptSnapshot;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptSourceSnapshot;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
 * Coordinates canonical receipt generation across the database and document storage
 * boundaries. The database uniqueness constraint remains the final concurrency guard.
 */
@Service
public class PaymentReceiptApplicationService {

    private static final String CONTENT_TYPE = "application/pdf";
    private static final String FALLBACK_RENDERER_VERSION = "receipt-renderer-v1";
    private static final Logger LOGGER =
            LoggerFactory.getLogger(PaymentReceiptApplicationService.class);

    private final PaymentReceiptStore receiptStore;
    private final PaymentReceiptQuery receiptQuery;
    private final PaymentReceiptSnapshotQuery snapshotQuery;
    private final PaymentReceiptOrganizationQuery organizationQuery;
    private final PaymentReceiptRenderer renderer;
    private final PaymentReceiptStorage storage;
    private final PaymentReceiptNumberGenerator numberGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public PaymentReceiptApplicationService(
            PaymentReceiptStore receiptStore,
            PaymentReceiptQuery receiptQuery,
            PaymentReceiptSnapshotQuery snapshotQuery,
            PaymentReceiptOrganizationQuery organizationQuery,
            PaymentReceiptRenderer renderer,
            PaymentReceiptStorage storage,
            PaymentReceiptNumberGenerator numberGenerator,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.receiptStore = Objects.requireNonNull(receiptStore);
        this.receiptQuery = Objects.requireNonNull(receiptQuery);
        this.snapshotQuery = Objects.requireNonNull(snapshotQuery);
        this.organizationQuery = Objects.requireNonNull(organizationQuery);
        this.renderer = Objects.requireNonNull(renderer);
        this.storage = Objects.requireNonNull(storage);
        this.numberGenerator = Objects.requireNonNull(numberGenerator);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Convenience constructor retaining a small, dependency-free composition root for
     * callers that do not need to replace the receipt-number generator.
     */
    public PaymentReceiptApplicationService(
            PaymentReceiptStore receiptStore,
            PaymentReceiptQuery receiptQuery,
            PaymentReceiptSnapshotQuery snapshotQuery,
            PaymentReceiptOrganizationQuery organizationQuery,
            PaymentReceiptRenderer renderer,
            PaymentReceiptStorage storage,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this(receiptStore, receiptQuery, snapshotQuery, organizationQuery, renderer, storage,
                () -> "REC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 28)
                        .toUpperCase(java.util.Locale.ROOT),
                eventPublisher, clock);
    }

    /**
     * Generates or returns the single canonical receipt for a confirmed payment.
     * Files are written before metadata, and are deleted if metadata cannot become
     * canonical. A duplicate insert is resolved by reading the winning row.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PaymentReceiptDetails generate(
            GeneratePaymentReceiptCommand command,
            AuthenticatedActor actor) {
        if (command == null) {
            throw new PaymentReceiptValidationException("Receipt generation command is required.");
        }
        requireActor(actor);
        UUID paymentId = command.paymentId();

        Optional<PaymentReceiptDetails> existing = receiptQuery.findByPaymentId(paymentId);
        if (existing.isPresent()) {
            return existing.get();
        }

        PaymentReceiptSourceSnapshot source = snapshotQuery.findByPaymentId(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        PaymentReceiptPolicy.requireGenerationAllowed(paymentId, source.paymentStatus());

        // PostgreSQL TIMESTAMPTZ stores microsecond precision. Normalize the
        // server timestamp before rendering and persisting so the immutable
        // in-memory candidate is byte-for-byte equivalent to its database row.
        Instant generatedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String receiptNumber = numberGenerator.next();
        if (receiptNumber == null || receiptNumber.isBlank()) {
            throw new PaymentReceiptValidationException("Receipt number generation failed.");
        }

        PaymentReceiptSnapshot snapshot = new PaymentReceiptSnapshot(
                receiptNumber,
                source.paymentId(),
                source.paymentCode(),
                source.paymentStatus(),
                source.clientCode(),
                source.clientDisplayName(),
                source.membershipCode(),
                source.planName(),
                source.promotionName(),
                source.membershipPeriodNumber(),
                source.periodStartsOn(),
                source.periodEndsOn(),
                source.listPrice(),
                source.discountAmount(),
                source.amount(),
                source.currency(),
                source.paymentMethod(),
                source.paidAt(),
                generatedAt,
                actor.id(),
                actor.username(),
                isTestMode(source));

        PaymentReceiptOrganization organization = organizationQuery.findCurrent();
        PaymentReceiptDocument document = renderer.render(snapshot, organization);
        if (document == null) {
            throw new PaymentReceiptRenderException(
                    "Payment receipt document could not be rendered.", null);
        }

        UUID receiptId = UUID.randomUUID();
        String storageKey = storage.generateStorageKey(receiptId);
        if (storageKey == null || storageKey.isBlank()) {
            throw new PaymentReceiptStorageException("Payment receipt storage key is invalid.");
        }
        PaymentReceiptDetails candidate = details(
                receiptId, snapshot, document, renderer.rendererVersion());

        try {
            // Storage is deliberately staged before the metadata insert. A provider
            // that fails after writing is still compensated by the same path.
            storage.store(storageKey, document);
            PaymentReceiptDetails persisted = receiptStore.save(candidate);
            if (persisted == null) {
                throw new PaymentReceiptDataAccessException(
                        "Payment receipt could not be persisted.", null);
            }
            ensureCanonical(persisted, candidate, document);
            publishAfterCommit(event(persisted), storageKey);
            return persisted;
        } catch (PaymentReceiptDuplicateException duplicate) {
            compensateOrThrow(storageKey, duplicate);
            return receiptQuery.findByPaymentId(paymentId)
                    .orElseThrow(() -> duplicate);
        } catch (RuntimeException failure) {
            compensateOrThrow(storageKey, failure);
            throw failure;
        }
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PaymentReceiptDetails findById(UUID receiptId) {
        requireIdentifier(receiptId, "Receipt id");
        return receiptQuery.findById(receiptId)
                .orElseThrow(() -> new PaymentReceiptNotFoundException(receiptId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PaymentReceiptDetails findByPaymentId(UUID paymentId) {
        requireIdentifier(paymentId, "Payment id");
        return receiptQuery.findByPaymentId(paymentId)
                .orElseThrow(() -> new PaymentReceiptNotFoundException(paymentId));
    }

    /**
     * Loads the immutable document associated with a canonical receipt after
     * validating it against the persisted metadata snapshot.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public PaymentReceiptContent downloadByPaymentId(UUID paymentId) {
        requireIdentifier(paymentId, "Payment id");
        PaymentReceiptDetails details = receiptQuery.findByPaymentId(paymentId)
                .orElseThrow(() -> new PaymentReceiptNotFoundException(paymentId));
        PaymentReceiptDocument document = storage.load(
                PaymentReceiptStorageKey.forReceipt(details.id()),
                details.contentType(),
                details.checksumSha256());
        if (document.sizeBytes() != details.sizeBytes()
                || !document.contentType().equals(details.contentType())
                || !document.checksumSha256().equals(details.checksumSha256())) {
            throw new PaymentReceiptDataAccessException(
                    "Stored payment receipt does not match its metadata.", null);
        }
        return new PaymentReceiptContent(details, document);
    }

    private static PaymentReceiptDetails details(
            UUID receiptId,
            PaymentReceiptSnapshot snapshot,
            PaymentReceiptDocument document,
            String rendererVersion) {
        String safeRendererVersion = rendererVersion == null || rendererVersion.isBlank()
                ? FALLBACK_RENDERER_VERSION : rendererVersion.strip();
        return new PaymentReceiptDetails(
                receiptId,
                snapshot.receiptNumber(),
                snapshot.paymentId(),
                snapshot.paymentCode(),
                snapshot.paymentStatus(),
                snapshot.clientCode(),
                snapshot.clientDisplayName(),
                snapshot.membershipCode(),
                snapshot.planName(),
                snapshot.promotionName(),
                snapshot.membershipPeriodNumber(),
                snapshot.periodStartsOn(),
                snapshot.periodEndsOn(),
                snapshot.listPrice(),
                snapshot.discountAmount(),
                snapshot.amount(),
                snapshot.currency(),
                snapshot.paymentMethod(),
                snapshot.paidAt(),
                snapshot.generatedAt(),
                snapshot.generatedByUserId(),
                snapshot.generatedByDisplayName(),
                snapshot.testMode(),
                document.contentType(),
                document.sizeBytes(),
                document.checksumSha256(),
                safeRendererVersion,
                0L);
    }

    private static PaymentReceiptGenerated event(PaymentReceiptDetails details) {
        return new PaymentReceiptGenerated(
                details.id(),
                details.receiptNumber(),
                details.paymentId(),
                details.paymentCode(),
                details.paymentStatus(),
                details.amount(),
                details.currency(),
                details.generatedByUserId(),
                details.generatedByDisplayName(),
                details.testMode(),
                details.generatedAt());
    }

    private static boolean isTestMode(PaymentReceiptSourceSnapshot source) {
        // CARD is currently created only by the Stripe Test Mode confirmation path.
        return source.paymentMethod() == PaymentMethod.CARD;
    }

    private static void ensureCanonical(
            PaymentReceiptDetails persisted,
            PaymentReceiptDetails candidate,
            PaymentReceiptDocument document) {
        if (!candidate.id().equals(persisted.id())
                || !candidate.receiptNumber().equals(persisted.receiptNumber())
                || !candidate.paymentId().equals(persisted.paymentId())
                || !candidate.paymentCode().equals(persisted.paymentCode())
                || persisted.paymentStatus() != candidate.paymentStatus()
                || !candidate.clientCode().equals(persisted.clientCode())
                || !candidate.clientDisplayName().equals(persisted.clientDisplayName())
                || !candidate.membershipCode().equals(persisted.membershipCode())
                || !candidate.planName().equals(persisted.planName())
                || !Objects.equals(candidate.promotionName(), persisted.promotionName())
                || candidate.membershipPeriodNumber() != persisted.membershipPeriodNumber()
                || !candidate.periodStartsOn().equals(persisted.periodStartsOn())
                || !candidate.periodEndsOn().equals(persisted.periodEndsOn())
                || candidate.listPrice().compareTo(persisted.listPrice()) != 0
                || candidate.discountAmount().compareTo(persisted.discountAmount()) != 0
                || candidate.amount().compareTo(persisted.amount()) != 0
                || !candidate.currency().equals(persisted.currency())
                || candidate.paymentMethod() != persisted.paymentMethod()
                || !candidate.paidAt().equals(persisted.paidAt())
                || !candidate.generatedAt().equals(persisted.generatedAt())
                || !candidate.generatedByUserId().equals(persisted.generatedByUserId())
                || !Objects.equals(candidate.generatedByDisplayName(), persisted.generatedByDisplayName())
                || candidate.testMode() != persisted.testMode()
                || !CONTENT_TYPE.equals(persisted.contentType())
                || persisted.sizeBytes() != document.sizeBytes()
                || !persisted.checksumSha256().equals(document.checksumSha256())) {
            throw new PaymentReceiptDataAccessException(
                    "Payment receipt persistence returned a non-canonical record.", null);
        }
    }

    private void compensateOrThrow(String storageKey, RuntimeException failure) {
        try {
            storage.delete(storageKey);
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
            throw new PaymentReceiptStorageException(
                    "Payment receipt artifact cleanup failed.", failure);
        }
    }

    private void publishAfterCommit(PaymentReceiptGenerated event, String storageKey) {
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
                    } catch (RuntimeException ignored) {
                        // There is no caller to notify after rollback. Keep the log
                        // free of the storage path and original exception details.
                        LOGGER.error("Payment receipt cleanup failed after rollback: {}",
                                ignored.getClass().getSimpleName());
                    }
                }
            }
        });
    }

    private static void requireActor(AuthenticatedActor actor) {
        if (actor == null || actor.id() == null
                || actor.username() == null || actor.username().isBlank()) {
            throw new PaymentReceiptValidationException("Authenticated actor is required.");
        }
    }

    private static void requireIdentifier(UUID value, String label) {
        if (value == null) {
            throw new PaymentReceiptValidationException(label + " is required.");
        }
    }
}
