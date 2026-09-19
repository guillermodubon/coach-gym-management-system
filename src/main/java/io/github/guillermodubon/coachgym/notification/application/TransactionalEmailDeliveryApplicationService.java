package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.ComposedEmail;
import io.github.guillermodubon.coachgym.notification.EmailAttachment;
import io.github.guillermodubon.coachgym.notification.EmailAttemptResult;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryAttemptDetails;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryDetails;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryLifecycleEvent;
import io.github.guillermodubon.coachgym.notification.EmailDeliverySource;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryStatus;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.EmailSendResult;
import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryIdempotency;
import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryLifecyclePolicy;
import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValidationException;
import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValuePolicy;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Coordinates transactional email delivery without holding a database
 * transaction across SMTP I/O.
 *
 * <p>Persistence adapters provide transaction A (durable {@code PENDING}), a
 * short database-owned attempt lease, and transaction B (attempt plus
 * versioned finalization). No process-local lock is used as a correctness
 * authority, so concurrent backend instances coordinate through PostgreSQL.</p>
 */
@Service
public class TransactionalEmailDeliveryApplicationService {

    private final EmailDeliverySourceResolver sourceResolver;
    private final EmailComposer composer;
    private final EmailSender sender;
    private final EmailDeliveryStore deliveryStore;
    private final EmailDeliveryQuery deliveryQuery;
    private final EmailDeliveryLifecyclePolicy lifecyclePolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public TransactionalEmailDeliveryApplicationService(
            EmailDeliverySourceResolver sourceResolver,
            EmailComposer composer,
            EmailSender sender,
            EmailDeliveryStore deliveryStore,
            EmailDeliveryQuery deliveryQuery,
            EmailDeliveryLifecyclePolicy lifecyclePolicy,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.sourceResolver = Objects.requireNonNull(
                sourceResolver, "Email delivery source resolver is required.");
        this.composer = Objects.requireNonNull(composer, "Email composer is required.");
        this.sender = Objects.requireNonNull(sender, "Email sender is required.");
        this.deliveryStore = Objects.requireNonNull(
                deliveryStore, "Email delivery store is required.");
        this.deliveryQuery = Objects.requireNonNull(
                deliveryQuery, "Email delivery query is required.");
        this.lifecyclePolicy = Objects.requireNonNull(
                lifecyclePolicy, "Email delivery lifecycle policy is required.");
        this.eventPublisher = Objects.requireNonNull(
                eventPublisher, "Email lifecycle event publisher is required.");
        this.clock = Objects.requireNonNull(clock, "Application clock is required.");
    }

    /** Convenience constructor using the domain default retry bound. */
    public TransactionalEmailDeliveryApplicationService(
            EmailDeliverySourceResolver sourceResolver,
            EmailComposer composer,
            EmailSender sender,
            EmailDeliveryStore deliveryStore,
            EmailDeliveryQuery deliveryQuery,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this(sourceResolver, composer, sender, deliveryStore, deliveryQuery,
                new EmailDeliveryLifecyclePolicy(), eventPublisher, clock);
    }

    /** Requests the canonical receipt email for a confirmed payment. */
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public EmailDeliveryDetails requestPaymentReceiptEmail(
            RequestPaymentReceiptEmailCommand command,
            AuthenticatedActor actor) {
        requireCommand(command, "Payment receipt email command");
        requireActor(actor);
        EmailDeliverySource source = sourceResolver.resolvePaymentReceiptForPayment(
                command.paymentId());
        return requestCanonical(source, actor.id(), actor.username());
    }

    /** Requests the current canonical access credential email for a client. */
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public EmailDeliveryDetails requestAccessCredentialEmail(
            RequestAccessCredentialEmailCommand command,
            AuthenticatedActor actor) {
        requireCommand(command, "Access credential email command");
        requireActor(actor);
        EmailDeliverySource source = sourceResolver.resolveCurrentAccessCredentialForClient(
                command.clientId());
        return requestCanonical(source, actor.id(), actor.username());
    }

    /** Retries one failed delivery using its persisted server-owned snapshots. */
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public EmailDeliveryDetails retryEmailDelivery(
            RetryEmailDeliveryCommand command,
            AuthenticatedActor actor) {
        requireCommand(command, "Email delivery retry command");
        requireActor(actor);

        EmailDeliveryDetails delivery = deliveryQuery.findById(command.deliveryId())
                .orElseThrow(() -> new EmailDeliveryNotFoundException(command.deliveryId()));
        lifecyclePolicy.requireRetryAllowed(delivery, command.expectedVersion());

        EmailDeliverySource resolved = sourceResolver.resolve(
                delivery.deliveryType(), delivery.sourceResourceId());
        validateRetrySource(delivery, resolved);
        EmailDeliverySource retrySource = withPersistedRecipient(delivery, resolved);
        ComposedEmail composed = composeSafely(retrySource);
        EmailMessage message = messageForRetry(delivery, retrySource, composed);
        EmailDeliveryClaim claim = claimForAttempt(delivery);
        return sendAndFinalize(delivery, message, actor.id(), actor.username(), claim);
    }

    /** Returns one safe operational delivery snapshot for authenticated staff. */
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public EmailDeliveryDetails findById(UUID deliveryId) {
        if (deliveryId == null) {
            throw new EmailDeliveryValidationException("Email delivery id is required.");
        }
        return deliveryQuery.findById(deliveryId)
                .orElseThrow(() -> new EmailDeliveryNotFoundException(deliveryId));
    }

    /** Returns bounded, allowlisted operational delivery history. */
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public EmailDeliveryPage findAll(EmailDeliverySearchQuery query) {
        if (query == null) {
            throw new EmailDeliveryValidationException("Email delivery search query is required.");
        }
        return deliveryQuery.findAll(query);
    }

    /** Returns append-only attempts for one delivery. */
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public List<EmailDeliveryAttemptDetails> findAttempts(UUID deliveryId) {
        if (deliveryId == null) {
            throw new EmailDeliveryValidationException("Email delivery id is required.");
        }
        if (deliveryQuery.findById(deliveryId).isEmpty()) {
            throw new EmailDeliveryNotFoundException(deliveryId);
        }
        return List.copyOf(deliveryQuery.findAttempts(deliveryId));
    }

    private EmailDeliveryDetails requestCanonical(
            EmailDeliverySource source,
            UUID actorUserId,
            String actorIdentifier) {
        ComposedEmail composed = composeSafely(source);
        verifyComposedSource(source, composed);
        String digest = EmailDeliveryIdempotency.derive(
                source.deliveryType(),
                source.sourceResourceId(),
                source.recipient(),
                composed.templateVersion());

        Optional<EmailDeliveryDetails> existing = deliveryQuery
                .findByIdempotencyKeyDigest(digest);
        if (existing.isPresent()) {
            // PENDING is intentionally returned as-is. A crashed process must
            // not be converted into an uncontrolled automatic resend.
            return existing.get();
        }

        Instant requestedAt = now();
        EmailDeliveryDetails pending = pendingDetails(
                source, composed, digest, actorUserId, requestedAt);
        EmailDeliveryDetails persisted;
        try {
            persisted = deliveryStore.createPending(pending);
        } catch (EmailDeliveryDuplicateException duplicate) {
            // Another instance may have won the PostgreSQL uniqueness race.
            return deliveryQuery.findByIdempotencyKeyDigest(digest)
                    .orElseThrow(() -> duplicate);
        }
        if (persisted == null) {
            throw new EmailDeliveryDataAccessException(
                    "Email delivery could not be persisted.", null);
        }
        EmailDeliveryClaim claim = claimForAttempt(persisted);
        return sendAndFinalize(
                persisted, composed.message(), actorUserId, actorIdentifier, claim);
    }

    private EmailDeliveryDetails sendAndFinalize(
            EmailDeliveryDetails delivery,
            EmailMessage message,
            UUID actorUserId,
            String actorIdentifier,
            EmailDeliveryClaim claim) {
        Instant startedAt = atOrAfter(delivery.requestedAt(), now());
        EmailSendResult result = sendSafely(message);
        Instant completedAt = atOrAfter(startedAt, now());
        int attemptNumber = delivery.attemptCount() + 1;
        EmailDeliveryAttemptDetails attempt = new EmailDeliveryAttemptDetails(
                UUID.randomUUID(),
                delivery.id(),
                attemptNumber,
                result.result(),
                startedAt,
                completedAt,
                result.failureCode(),
                result.failureMessage(),
                actorUserId,
                result.providerMessageId());
        EmailDeliveryStatus finalStatus = result.result() == EmailAttemptResult.SENT
                ? EmailDeliveryStatus.SENT : EmailDeliveryStatus.FAILED;
        Instant sentAt = finalStatus == EmailDeliveryStatus.SENT ? completedAt : null;
        EmailDeliveryDetails finalized = deliveryStore.appendAttemptAndFinalize(
                attempt,
                finalStatus,
                result.failureCode(),
                result.failureMessage(),
                completedAt,
                sentAt,
                claim.expectedVersion(),
                claim.claimToken());
        if (finalized == null) {
            throw new EmailDeliveryDataAccessException(
                    "Email delivery finalization returned no persisted value.", null);
        }
        eventPublisher.publishEvent(new EmailDeliveryLifecycleEvent(
                finalized.id(),
                finalized.deliveryType(),
                delivery.status(),
                finalized.status(),
                result.result(),
                attemptNumber,
                result.failureCode(),
                actorUserId,
                completedAt,
                finalized.sourceResourceId(),
                finalized.clientId(),
                EmailDeliveryValuePolicy.maskRecipient(finalized.recipientSnapshot()),
                actorIdentifier));
        return finalized;
    }

    private EmailDeliveryClaim claimForAttempt(EmailDeliveryDetails delivery) {
        Instant claimedAt = now();
        Instant expiresAt = claimedAt.plus(lifecyclePolicy.stalePendingThreshold());
        Optional<EmailDeliveryClaim> claim = deliveryStore.claimForAttempt(
                delivery.id(), delivery.version(), claimedAt, expiresAt);
        if (claim.isEmpty()) {
            throw new EmailDeliveryClaimConflictException(delivery.id());
        }
        return claim.get();
    }

    private EmailSendResult sendSafely(EmailMessage message) {
        try {
            EmailSendResult result = sender.send(message);
            return result == null
                    ? EmailSendResult.failed(
                            EmailDeliveryFailureCode.UNEXPECTED_FAILURE,
                            "The email could not be delivered.")
                    : result;
        } catch (RuntimeException exception) {
            // Provider and transport details never cross into persistence.
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.UNEXPECTED_FAILURE,
                    "The email could not be delivered.");
        }
    }

    private ComposedEmail composeSafely(EmailDeliverySource source) {
        try {
            ComposedEmail composed = composer.compose(source);
            if (composed == null) {
                throw new EmailCompositionException("Email composition returned no message.");
            }
            return composed;
        } catch (EmailCompositionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EmailCompositionException(
                    "Email message could not be composed safely.", exception);
        }
    }

    private EmailDeliveryDetails pendingDetails(
            EmailDeliverySource source,
            ComposedEmail composed,
            String digest,
            UUID actorUserId,
            Instant requestedAt) {
        EmailAttachment attachment = source.attachment();
        return new EmailDeliveryDetails(
                UUID.randomUUID(),
                source.deliveryType(),
                source.sourceResourceId(),
                source.clientId(),
                source.recipient(),
                composed.message().subject(),
                composed.templateVersion(),
                source.deliveryType().name(),
                source.sourceResourceId(),
                attachment.filename(),
                attachment.contentType(),
                attachment.sizeBytes(),
                attachment.checksumSha256(),
                digest,
                EmailDeliveryStatus.PENDING,
                0,
                null,
                null,
                requestedAt,
                actorUserId,
                null,
                null,
                requestedAt,
                requestedAt,
                0);
    }

    private EmailMessage messageForRetry(
            EmailDeliveryDetails delivery,
            EmailDeliverySource source,
            ComposedEmail composed) {
        if (!delivery.templateVersion().equals(composed.templateVersion())) {
            throw new EmailCompositionException(
                    "The persisted email template version is no longer available.");
        }
        verifyComposedSource(source, composed);
        EmailMessage message = composed.message();
        if (delivery.subjectSnapshot().equals(message.subject())) {
            return message;
        }
        // Subject is a persisted snapshot. Reuse it even when harmless client
        // display data changed between the original attempt and an explicit retry.
        try {
            return new EmailMessage(
                    delivery.recipientSnapshot(),
                    message.fromAddress(),
                    message.fromName(),
                    message.replyTo(),
                    delivery.subjectSnapshot(),
                    message.plainTextBody(),
                    message.htmlBody(),
                    message.attachment());
        } catch (IllegalArgumentException exception) {
            throw new EmailCompositionException(
                    "Persisted email message could not be reused safely.", exception);
        }
    }

    private void validateRetrySource(
            EmailDeliveryDetails delivery,
            EmailDeliverySource source) {
        if (source.deliveryType() != delivery.deliveryType()
                || !source.sourceResourceId().equals(delivery.sourceResourceId())
                || !source.clientId().equals(delivery.clientId())) {
            throw new EmailDeliveryDataAccessException(
                    "Email delivery source association is invalid.", null);
        }
        EmailAttachment attachment = source.attachment();
        if (!delivery.attachmentFilename().equals(attachment.filename())
                || !delivery.attachmentContentType().equals(attachment.contentType())
                || delivery.attachmentSizeBytes() != attachment.sizeBytes()
                || !delivery.attachmentChecksumSha256().equals(attachment.checksumSha256())) {
            throw new EmailDeliveryAttachmentException(
                    delivery.deliveryType(),
                    delivery.sourceResourceId(),
                    "The canonical email attachment no longer matches the delivery snapshot.");
        }
    }

    private static EmailDeliverySource withPersistedRecipient(
            EmailDeliveryDetails delivery,
            EmailDeliverySource source) {
        return new EmailDeliverySource(
                source.deliveryType(),
                source.sourceResourceId(),
                source.clientId(),
                delivery.recipientSnapshot(),
                source.attachment(),
                source.templateData());
    }

    private static void verifyComposedSource(
            EmailDeliverySource source,
            ComposedEmail composed) {
        EmailMessage message = composed.message();
        EmailAttachment expected = source.attachment();
        EmailAttachment actual = message.attachment();
        if (!source.recipient().equals(message.recipient())
                || !expected.filename().equals(actual.filename())
                || !expected.contentType().equals(actual.contentType())
                || expected.sizeBytes() != actual.sizeBytes()
                || !expected.checksumSha256().equals(actual.checksumSha256())) {
            throw new EmailCompositionException(
                    "Email composition attempted to replace server-owned delivery data.");
        }
        // Force the same canonical validation path used by the persistence
        // snapshot, including recipient and template bounds.
        EmailDeliveryValuePolicy.normalizeRecipient(message.recipient());
        EmailDeliveryValuePolicy.normalizeTemplateVersion(composed.templateVersion());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static Instant atOrAfter(Instant lowerBound, Instant candidate) {
        return candidate.isBefore(lowerBound) ? lowerBound : candidate;
    }

    private static void requireCommand(Object command, String name) {
        if (command == null) {
            throw new EmailDeliveryValidationException(name + " is required.");
        }
    }

    private static void requireActor(AuthenticatedActor actor) {
        if (actor == null || actor.id() == null
                || actor.username() == null || actor.username().isBlank()) {
            throw new EmailDeliveryValidationException("Authenticated actor is required.");
        }
    }

}
