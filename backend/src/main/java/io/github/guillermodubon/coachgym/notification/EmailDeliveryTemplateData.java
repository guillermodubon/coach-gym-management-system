package io.github.guillermodubon.coachgym.notification;

import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValuePolicy;

/** Safe, non-secret values allowed in a transactional email template. */
public record EmailDeliveryTemplateData(
        String organizationName,
        String clientDisplayName,
        String clientCode,
        String primaryReference,
        String secondaryReference,
        String status,
        String eventTimestamp,
        String amount,
        String currency,
        String membershipCode,
        String planName,
        String credentialCode,
        boolean testMode) {

    private static final int MAX_VALUE_LENGTH = 200;

    public EmailDeliveryTemplateData {
        organizationName = optional(organizationName, "Organization name");
        clientDisplayName = optional(clientDisplayName, "Client display name");
        clientCode = optional(clientCode, "Client code");
        primaryReference = optional(primaryReference, "Primary email reference");
        secondaryReference = optional(secondaryReference, "Secondary email reference");
        status = optional(status, "Email status");
        eventTimestamp = optional(eventTimestamp, "Email event timestamp");
        amount = optional(amount, "Email amount");
        currency = optional(currency, "Email currency");
        membershipCode = optional(membershipCode, "Membership code");
        planName = optional(planName, "Plan name");
        credentialCode = optional(credentialCode, "Credential code");
    }

    /** Returns a template context without optional business values. */
    public static EmailDeliveryTemplateData empty() {
        return new EmailDeliveryTemplateData(
                null, null, null, null, null, null, null, null, null, null, null, null, false);
    }

    private static String optional(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException(field + " must not exceed " + MAX_VALUE_LENGTH + " characters.");
        }
        EmailDeliveryValuePolicy.rejectHeaderInjection(normalized, field);
        return normalized;
    }
}
