package io.github.guillermodubon.coachgym.notification.infrastructure.gmail;

/** Configuration-only readiness seam; it never makes an OAuth or Gmail request. */
public final class GmailConfigurationReadiness {

    public enum Status {
        DISABLED,
        READY,
        INVALID_CONFIGURATION
    }

    private final GmailApiProperties apiProperties;
    private final GoogleOAuthProperties oauthProperties;

    public GmailConfigurationReadiness(
            GmailApiProperties apiProperties,
            GoogleOAuthProperties oauthProperties) {
        this.apiProperties = apiProperties;
        this.oauthProperties = oauthProperties;
    }

    public Status status(boolean emailEnabled) {
        return status(emailEnabled, apiProperties.senderAddress());
    }

    /** Validates Gmail readiness and alignment with the message From address. */
    public Status status(boolean emailEnabled, String configuredFromAddress) {
        if (!emailEnabled) {
            return Status.DISABLED;
        }
        return apiProperties.isValidWhenEnabled()
                        && oauthProperties.isValidWhenEnabled()
                        && senderIdentityMatches(configuredFromAddress)
                ? Status.READY
                : Status.INVALID_CONFIGURATION;
    }

    public boolean senderIdentityMatches(String configuredFromAddress) {
        return configuredFromAddress != null
                && apiProperties.senderAddress() != null
                && apiProperties.senderAddress().equalsIgnoreCase(configuredFromAddress.strip());
    }
}
