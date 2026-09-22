package io.github.guillermodubon.coachgym.user.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import io.github.guillermodubon.coachgym.user.SelectActiveBranchCommand;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Branch selection request; the server validates assignment and activity. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record SelectActiveBranchRequest(@NotNull UUID branchId) {

    SelectActiveBranchCommand toCommand() {
        return new SelectActiveBranchCommand(branchId);
    }

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported active branch request field: " + name);
    }
}
