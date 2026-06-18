package com.immobilier.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateStatusRequest {
    @NotBlank(message = "Le statut est obligatoire")
    private String statut;

    private Long propertyId;

    private Integer rentalDurationMonths;
}