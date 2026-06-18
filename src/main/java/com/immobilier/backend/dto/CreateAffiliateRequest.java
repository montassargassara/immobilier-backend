package com.immobilier.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class CreateAffiliateRequest {

    @NotBlank(message = "L'email est requis")
    @Email(message = "Email invalide")
    private String email;

    @NotBlank(message = "Le mot de passe est requis")
    @Size(min = 6, message = "Le mot de passe doit contenir au moins 6 caractères")
    private String password;

    @NotBlank(message = "Le nom est requis")
    private String nom;

    @NotBlank(message = "Le prénom est requis")
    private String prenom;

    private String telephone;

    // Optional: JUNIOR | MID | SENIOR | EXPERT
    private String experienceLevel;

    private String notes;

    // At least one region must be selected
    @NotEmpty(message = "Au moins une zone doit être sélectionnée")
    private List<RegionSelection> selectedRegions;
}
