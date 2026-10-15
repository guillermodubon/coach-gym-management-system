package io.github.guillermodubon.coachgym.access.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.guillermodubon.coachgym.access.application.QrAccessCheckInCommand;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialQrPayload;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Strict staff request for a decoded versioned QR access payload. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
        description = "Decoded QR payload only. Client, membership, decision, actor, and payment fields are server controlled.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record QrCheckInRequest(

        @NotBlank
        @Size(max = AccessCredentialQrPayload.MAX_LENGTH)
        @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "payload must not contain control characters")
        @Schema(
                description = "Canonical versioned payload produced by the Coach Gym credential issuer.",
                maxLength = AccessCredentialQrPayload.MAX_LENGTH,
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "cgac:v1:synthetic-placeholder-not-a-real-credential")
        String payload) {

    QrAccessCheckInCommand toCommand() {
        return QrAccessCheckInCommand.parse(payload);
    }

    /** Rejects fields that could attempt to override server-controlled state. */
    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported QR check-in request field.");
    }
}
