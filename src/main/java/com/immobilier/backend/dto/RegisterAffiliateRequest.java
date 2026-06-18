package com.immobilier.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * @deprecated Use {@link CreateAffiliateRequest} instead.
 * Kept temporarily to avoid breaking existing calls; delegates to the same fields.
 */
@Data
@Deprecated
public class RegisterAffiliateRequest {

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

    private String experienceLevel;
    private String notes;

    private List<RegionSelection> selectedRegions;
}
