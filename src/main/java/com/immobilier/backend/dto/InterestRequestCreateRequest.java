package com.immobilier.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterestRequestCreateRequest {
    @NotNull
    private Long propertyId;

    @NotBlank @Size(max = 160)
    private String fullName;

    @NotBlank @Size(min = 6, max = 60)
    private String telephone;

    @Size(max = 1000)
    private String message;

    @Positive
    private Double proposedBudget;
}
