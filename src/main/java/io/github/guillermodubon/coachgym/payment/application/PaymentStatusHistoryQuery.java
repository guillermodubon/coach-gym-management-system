package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentStatusHistoryPage;
import java.util.UUID;

/** Read port for paginated payment status history ordered newest first. */
public interface PaymentStatusHistoryQuery {

    PaymentStatusHistoryPage findByPaymentId(
            UUID paymentId,
            int page,
            int size);
}
