package com.immobilier.backend.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateUserRequest {
    private String nom;
    private String prenom;
    
    // ✅ Format tunisien
    @Pattern(regexp = "^(?:(?:\\+|00)216|0)?[2-9][0-9]{7}$|^$", 
             message = "Format de téléphone invalide (ex: 062547413 ou +21662547413)")
    private String telephone;
    
    private Boolean isActive;

    // Staff commission rate (%) — applies to COMMERCIAL / RESPONSABLE_COMMERCIAL
    private Double commissionRate;
}