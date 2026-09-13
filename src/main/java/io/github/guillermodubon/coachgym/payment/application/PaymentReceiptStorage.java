package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentReceiptDocument;
import java.util.UUID;

/** Technology-neutral port for canonical receipt document storage. */
public interface PaymentReceiptStorage {

    String generateStorageKey(UUID receiptId);

    void store(String storageKey, PaymentReceiptDocument document);

    PaymentReceiptDocument load(
            String storageKey,
            String contentType,
            String checksumSha256);

    void delete(String storageKey);
}
