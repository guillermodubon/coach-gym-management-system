package io.github.guillermodubon.coachgym.user.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record PasswordRecoveryAcknowledgement(String message) {
}
