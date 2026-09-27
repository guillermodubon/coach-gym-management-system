package io.github.guillermodubon.coachgym.user.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.guillermodubon.coachgym.user.RequestPasswordRecoveryCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record RequestPasswordRecoveryRequest(@NotBlank @Email @Size(max = 254) String email) {

    RequestPasswordRecoveryCommand toCommand() {
        return new RequestPasswordRecoveryCommand(email);
    }

    @Override
    public String toString() {
        return "RequestPasswordRecoveryRequest[emailPresent=" + (email != null) + ']';
    }

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported password recovery field: " + name);
    }
}
