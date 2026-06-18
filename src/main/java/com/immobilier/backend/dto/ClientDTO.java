package com.immobilier.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ClientDTO {
    private Long id;
    private String email;
    private String nom;
    private String prenom;
    private String telephone;
    private String role;
    private Boolean isActive;
    private LocalDateTime createdAt;
    
    // Client specific fields
    private Double budgetEstime;
    private String zoneRecherchee;
    private Long commercialId;
    private String commercialNom;
    private String commercialPrenom;
    
    // Visibility fields
    private Long createdBy;
    private String createdByName;
    private String visibilityType; // "PRIVATE_CLIENT" or "AGENCY_CLIENT"
    private Long agencyAdminId;
    private List<Long> sharedWithAgencyIds;
    private List<String> sharedWithAgencyNames;
    
    // Statistics
    private Integer nombreAchats;
    private Integer nombreLocations;
    private Integer nombreReservations;
    private Double totalAchats;
    
    // Affiliate specific fields
    private String codeAffiliation;
    private Double tauxCommission;
    private String source;
    private Double commissionGeneree;
    private Integer nombreVentesLiees;
    
    // Commercial notes
    private String derniereNote;
    private LocalDateTime derniereNoteDate;
}