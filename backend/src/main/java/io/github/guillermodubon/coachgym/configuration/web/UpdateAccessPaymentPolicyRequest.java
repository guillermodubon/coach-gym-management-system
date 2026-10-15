package io.github.guillermodubon.coachgym.configuration.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyValidationException;
import io.github.guillermodubon.coachgym.configuration.application.UpdateAccessPaymentPolicyCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Strict administrative request for the global access-payment policy.
 *
 * <p>The nullable wrappers preserve the distinction between an omitted field
 * and the value {@code false}/{@code 0}; actor and timestamp are always
 * resolved on the server.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
        name = "UpdateAccessPaymentPolicyRequest",
        description = "Administrative policy value and optimistic-lock version. Actor and timestamp are server controlled.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record UpdateAccessPaymentPolicyRequest(
        @JsonProperty("requireConfirmedPaymentForAccess")
        @NotNull
        @Schema(
                description = "Whether manual and QR access require a confirmed PAID payment.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "true")
        Boolean requireConfirmedPaymentForAccess,

        @JsonProperty("version")
        @NotNull
        @PositiveOrZero
        @Schema(
                description = "Expected current policy version used for optimistic locking.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                minimum = "0",
                example = "0")
        Long version) {

    /** Rejects fields that are not part of the public policy contract. */
    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported access payment policy request field: " + name);
    }

    UpdateAccessPaymentPolicyCommand toCommand() {
        if (requireConfirmedPaymentForAccess == null) {
            throw new AccessPaymentPolicyValidationException(
                    "requireConfirmedPaymentForAccess is required.");
        }
        if (version == null || version < 0) {
            throw new AccessPaymentPolicyValidationException(
                    "version must be a non-negative value.");
        }
        return new UpdateAccessPaymentPolicyCommand(
                requireConfirmedPaymentForAccess,
                version);
    }
}
