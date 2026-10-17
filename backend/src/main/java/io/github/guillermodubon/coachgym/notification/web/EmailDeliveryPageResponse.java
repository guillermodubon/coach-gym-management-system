package io.github.guillermodubon.coachgym.notification.web;

import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryPage;
import java.util.List;

/** Stable page projection for delivery history. */
record EmailDeliveryPageResponse(
        List<EmailDeliveryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static EmailDeliveryPageResponse from(EmailDeliveryPage page) {
        return new EmailDeliveryPageResponse(
                page.items().stream().map(EmailDeliveryResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}
