package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.payment.PaymentDetails;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDetails;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDocument;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptEmailSource;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptEmailSourceQuery;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptQuery;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptStorage;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptStorageKey;
import io.github.guillermodubon.coachgym.payment.application.PaymentStore;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Composes the payment-owned receipt metadata and canonical PDF storage port. */
@Repository
class JdbcPaymentReceiptEmailSourceQuery implements PaymentReceiptEmailSourceQuery {

    private final PaymentReceiptQuery receiptQuery;
    private final PaymentStore paymentStore;
    private final PaymentReceiptStorage receiptStorage;

    JdbcPaymentReceiptEmailSourceQuery(
            PaymentReceiptQuery receiptQuery,
            PaymentStore paymentStore,
            PaymentReceiptStorage receiptStorage) {
        this.receiptQuery = Objects.requireNonNull(receiptQuery);
        this.paymentStore = Objects.requireNonNull(paymentStore);
        this.receiptStorage = Objects.requireNonNull(receiptStorage);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentReceiptEmailSource> findByReceiptId(UUID receiptId) {
        if (receiptId == null) {
            throw new IllegalArgumentException("Receipt id is required.");
        }
        Optional<PaymentReceiptDetails> receipt = receiptQuery.findById(receiptId);
        return compose(receipt);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentReceiptEmailSource> findByPaymentId(UUID paymentId) {
        if (paymentId == null) {
            throw new IllegalArgumentException("Payment id is required.");
        }
        return compose(receiptQuery.findByPaymentId(paymentId));
    }

    private Optional<PaymentReceiptEmailSource> compose(
            Optional<PaymentReceiptDetails> receipt) {
        if (receipt.isEmpty()) {
            return Optional.empty();
        }
        PaymentReceiptDetails details = receipt.get();
        Optional<PaymentDetails> payment = paymentStore.findById(details.paymentId());
        if (payment.isEmpty() || !details.paymentId().equals(payment.get().id())) {
            return Optional.empty();
        }
        PaymentReceiptDocument document = receiptStorage.load(
                PaymentReceiptStorageKey.forReceipt(details.id()),
                details.contentType(),
                details.checksumSha256());
        return Optional.of(new PaymentReceiptEmailSource(
                details, payment.get().clientId(), document));
    }
}
