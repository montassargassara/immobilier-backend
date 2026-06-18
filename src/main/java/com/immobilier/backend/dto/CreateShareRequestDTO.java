package com.immobilier.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateShareRequestDTO {

    @NotEmpty(message = "Au moins une agence doit être sélectionnée")
    private List<Long> agencyAdminIds;

    @NotNull(message = "Le type de commission est obligatoire")
    private String commissionType; // PERCENTAGE | FIXED

    @NotNull(message = "La commission est obligatoire")
    @Min(value = 0, message = "La commission ne peut pas être négative")
    private Double commissionPercentage; // percentage value OR fixed TND amount

    private String message; // optional message to agency
}
