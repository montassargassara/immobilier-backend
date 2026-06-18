package com.immobilier.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClientPublicRegisterRequest {
    @NotBlank @Size(min = 2, max = 80)
    private String nom;

    @NotBlank @Size(min = 2, max = 80)
    private String prenom;

    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 8, max = 64)
    private String telephone;

    @NotBlank @Size(min = 6, max = 100)
    private String password;

    // Required CRM profile fields collected at registration time
    @NotNull(message = "Le budget est requis")
    @DecimalMin(value = "1", inclusive = true, message = "Le budget doit être positif")
    private Double budgetEstime;

    @NotBlank(message = "Le pays est requis") @Size(max = 100)
    private String pays;

    @NotBlank(message = "La ville est requise") @Size(max = 100)
    private String ville;
}
