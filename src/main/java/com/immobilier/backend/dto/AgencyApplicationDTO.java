package com.immobilier.backend.dto;

import lombok.Data;

@Data
public class AgencyApplicationDTO {
    private Long id;
    private Long userId;
    private String email;
    private String nom;
    private String prenom;
    private String agencyName;
    private String telephone;
    private String description;
    private String status;           // PENDING | APPROVED | REJECTED
    private String rejectionReason;
    private String createdAt;
    private String reviewedAt;
}
