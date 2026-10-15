package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentDetails;
import java.util.List;

public record PaymentPage(
        List<PaymentDetails> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public PaymentPage {
        items = List.copyOf(items);
    }
}
