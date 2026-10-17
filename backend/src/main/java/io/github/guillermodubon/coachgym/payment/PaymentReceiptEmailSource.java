package io.github.guillermodubon.coachgym.payment;

import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative receipt projection and its already-persisted PDF artifact.
 *
 * <p>The payment module owns the lookup and storage boundaries. Consumers
 * receive the canonical bytes only after the owning payment and artifact
 * metadata have been verified; receipt generation is never invoked here.</p>
 */
public record PaymentReceiptEmailSource(
        PaymentReceiptDetails receipt,
        UUID clientId,
        PaymentReceiptDocument document) {

    public PaymentReceiptEmailSource {
        receipt = Objects.requireNonNull(receipt, "Receipt details are required.");
        clientId = Objects.requireNonNull(clientId, "Receipt client id is required.");
        document = Objects.requireNonNull(document, "Receipt document is required.");
        if (!document.contentType().equals(receipt.contentType())
                || document.sizeBytes() != receipt.sizeBytes()
                || !document.checksumSha256().equals(receipt.checksumSha256())) {
            throw new IllegalArgumentException(
                    "Receipt document does not match its persisted metadata.");
        }
    }

    public UUID receiptId() {
        return receipt.id();
    }
}
