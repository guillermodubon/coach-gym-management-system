package io.github.guillermodubon.coachgym.reporting.application;

import io.github.guillermodubon.coachgym.reporting.DashboardNotificationDetails;
import java.util.UUID;

/** Read port for the authenticated user's unread notification indicator. */
public interface DashboardNotificationQuery {

    DashboardNotificationDetails summarize(UUID recipientUserId);
}
