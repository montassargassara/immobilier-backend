package com.immobilier.backend.dto;

import com.immobilier.backend.enums.AffiliateStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AffiliateProfileDTO {
    private Long id;
    private Long userId;
    private String email;
    private String nom;
    private String prenom;
    private String telephone;
    private Boolean isActive;

    private AffiliateStatus status;
    private String experienceLevel;
    private String notes;

    private Long reviewedById;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private String rejectionReason;

    private List<AffiliateRegionDTO> regions;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
