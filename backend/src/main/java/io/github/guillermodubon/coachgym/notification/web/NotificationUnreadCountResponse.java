package io.github.guillermodubon.coachgym.notification.web;

import io.github.guillermodubon.coachgym.notification.NotificationUnreadCount;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description =
                "Unread notification count for the authenticated user's inbox.")
public record NotificationUnreadCountResponse(

        @Schema(
                description =
                        "Current number of unread notifications.",
                minimum = "0",
                example = "3")
        long count) {

    static NotificationUnreadCountResponse from(
            NotificationUnreadCount unreadCount) {

        return new NotificationUnreadCountResponse(
                unreadCount.count());
    }
}