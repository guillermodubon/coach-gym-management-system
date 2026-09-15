package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.PaymentStatusHistoryPage;
import java.util.List;

record PaymentStatusHistoryPageResponse(
        List<PaymentStatusHistoryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty) {

    static PaymentStatusHistoryPageResponse from(
            PaymentStatusHistoryPage source) {
        return new PaymentStatusHistoryPageResponse(
                source.content().stream()
                        .map(PaymentStatusHistoryResponse::from)
                        .toList(),
                source.page(),
                source.size(),
                source.totalElements(),
                source.totalPages(),
                source.first(),
                source.last(),
                source.empty());
    }
}
