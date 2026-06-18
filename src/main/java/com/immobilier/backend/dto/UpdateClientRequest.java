package com.immobilier.backend.dto;

import lombok.Data;
import java.util.List;

import jakarta.validation.constraints.Pattern;

@Data
public class UpdateClientRequest {
    private String nom;
    private String prenom;
    
    // ✅ Format tunisien
    @Pattern(regexp = "^(?:(?:\\+|00)216|0)?[2-9][0-9]{7}$|^$", 
             message = "Format de téléphone invalide (ex: 062547413 ou +21662547413)")
    private String telephone;
    
    private Double budgetEstime;
    private String zoneRecherchee;
    private Long commercialId;
    private Boolean isActive;

    // For affiliate only — explicit country/city take precedence over the legacy
    // joined `zoneRecherchee` string. When both are provided, the service rebuilds
    // zoneRecherchee = "country, city" and updates the affiliate's AffiliateRegion.
    private String country;
    private String city;

    // For affiliate only
    private String codeAffiliation;
    private Double tauxCommission;
    private String source;
    
    // Permissions (optionnel, peut être supprimé)
    private List<Long> authorizedUserIds;
}