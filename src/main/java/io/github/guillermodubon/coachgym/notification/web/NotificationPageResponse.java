package io.github.guillermodubon.coachgym.notification.web;

import io.github.guillermodubon.coachgym.notification.application.NotificationPage;
import java.util.List;

public record NotificationPageResponse(
        List<NotificationResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static NotificationPageResponse from(NotificationPage page) {
        return new NotificationPageResponse(
                page.items().stream()
                        .map(NotificationResponse::from)
                        .toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}
