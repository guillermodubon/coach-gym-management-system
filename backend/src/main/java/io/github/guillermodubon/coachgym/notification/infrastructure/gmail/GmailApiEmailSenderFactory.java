package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

import io.github.guillermodubon.coachgym.notification.application.EmailSender;

/** Creates the selected Gmail transport without registering a second EmailSender bean. */
public final class GmailApiEmailSenderFactory {

    private final GmailApiProperties apiProperties;
    private final GoogleOAuthTokenClient tokenClient;
    private final GmailOperationalMetrics metrics;

    GmailApiEmailSenderFactory(
            GmailApiProperties apiProperties,
            GoogleOAuthTokenClient tokenClient,
            GmailOperationalMetrics metrics) {
        this.apiProperties = apiProperties;
        this.tokenClient = tokenClient;
        this.metrics = metrics;
    }

    public EmailSender createSender() {
        return new GmailApiEmailSenderAdapter(
                GmailApiEmailSenderAdapter.createHttpClient(apiProperties),
                apiProperties,
                tokenClient,
                metrics);
    }
}
