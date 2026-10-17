package io.github.guillermodubon.coachgym.equipment.web;

import io.github.guillermodubon.coachgym.equipment.application.command.RegisterEquipmentCommand;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

@Schema(
        description = """
                Request for registering equipment.

                The identifier, equipment number, equipment code, status,
                retirement information, audit fields, timestamps and version
                are controlled by the server and are not accepted.
                """)
public record RegisterEquipmentRequest(

        @NotNull
        @Schema(
                description = "Active equipment-category identifier.",
                requiredMode =
                        Schema.RequiredMode.REQUIRED)
        UUID categoryId,

        @NotBlank
        @Size(max = 160)
        @Schema(
                description = "Equipment name.",
                example = "Commercial Treadmill",
                maxLength = 160,
                requiredMode =
                        Schema.RequiredMode.REQUIRED)
        String name,

        @Size(max = 120)
        @Schema(
                description = "Equipment manufacturer.",
                example = "LifeFitness",
                maxLength = 120)
        String manufacturer,

        @Size(max = 120)
        @Schema(
                description = "Equipment model.",
                example = "T5",
                maxLength = 120)
        String model,

        @Size(max = 120)
        @Schema(
                description = """
                        Optional serial number. Must be unique when supplied.
                        """,
                example = "SN-001",
                maxLength = 120)
        String serialNumber,

        @Size(max = 160)
        @Schema(
                description = "Physical equipment location.",
                example = "Cardio Floor",
                maxLength = 160)
        String location,

        @Schema(
                description = "Optional operational notes.")
        String notes,

        @Schema(
                description = "Optional purchase date.",
                example = "2025-01-15")
        LocalDate purchasedOn) {

    RegisterEquipmentCommand toCommand() {
        EquipmentDefinition definition =
                EquipmentDefinition.create(
                        categoryId,
                        name,
                        manufacturer,
                        model,
                        serialNumber,
                        location,
                        notes,
                        purchasedOn);

        return new RegisterEquipmentCommand(
                definition);
    }
}