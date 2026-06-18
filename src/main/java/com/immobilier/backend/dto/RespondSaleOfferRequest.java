package com.immobilier.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RespondSaleOfferRequest {

    @NotBlank(message = "La réponse est requise")
    @Pattern(regexp = "ACCEPTED|REJECTED", message = "La réponse doit être ACCEPTED ou REJECTED")
    private String response;

    // Mandatory when response = REJECTED
    private String rejectionReason;
}
