package com.immobilier.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class CreateClientRequest {
    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Email invalide")
    private String email;
    
    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 6, message = "Le mot de passe doit contenir au moins 6 caractères")
    private String password;
    
    @NotBlank(message = "Le nom est obligatoire")
    private String nom;
    
    @NotBlank(message = "Le prénom est obligatoire")
    private String prenom;
    
    @Pattern(regexp = "^(?:(?:\\+|00)216|0)?[2-9][0-9]{7}$|^$", 
             message = "Format de téléphone invalide (ex: 062547413 ou +21662547413)")
    private String telephone;
    
    private Double budgetEstime;
    private String zoneRecherchee;
    private Long commercialId;
    
    // For affiliate
    private String clientType; // "NORMAL" or "AFFILIATE"
    private String codeAffiliation;
    private Double tauxCommission;
    private String source;
    
    // ========== NOUVEAUX CHAMPS DE VISIBILITÉ ==========
    private String visibilityType; // "PRIVATE_CLIENT" or "AGENCY_CLIENT"
    private Long targetAgencyAdminId; // For AGENCY_CLIENT
    private List<Long> sharedAgencyIds; // For PRIVATE_CLIENT (list of ADMIN ids)
}