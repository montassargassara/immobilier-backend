package com.immobilier.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ShareRequestResponseDTO {

    @NotBlank(message = "La réponse est obligatoire")
    @Pattern(regexp = "ACCEPTED|REJECTED", message = "La réponse doit être ACCEPTED ou REJECTED")
    private String response; // ACCEPTED | REJECTED

    private String rejectionReason; // optional, meaningful when response = REJECTED
}
