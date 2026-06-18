package com.immobilier.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateAgencyRequest {

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

    @NotBlank(message = "Le nom de l'agence est requis")
    private String agencyName;

    private String telephone;

    private String description;
}
