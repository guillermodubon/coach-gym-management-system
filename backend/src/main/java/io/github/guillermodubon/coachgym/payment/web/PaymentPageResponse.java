package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.application.PaymentPage;
import java.util.List;

public record PaymentPageResponse(
        List<PaymentResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static PaymentPageResponse from(PaymentPage page) {
        return new PaymentPageResponse(
                page.items().stream().map(PaymentResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}
