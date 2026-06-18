package com.immobilier.backend.dto;

import com.immobilier.backend.enums.ShareRequestStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PropertyShareRequestDTO {

    private Long id;
    private ShareRequestStatus status;

    // Property snapshot (so agency can decide without a separate fetch)
    private Long propertyId;
    private String propertyTitle;
    private String propertyType;
    private String propertyStatut;
    private Double propertyPrixVente;
    private Double propertyPrixLocation;
    private String propertyAdresse;
    private String propertyCity;
    private String propertyCountry;
    private Double propertySurface;
    private Integer propertyNbChambres;
    private String propertyMainImageUrl;

    // Commission terms agreed for this share
    private Double commissionPercentage;
    private String commissionType;          // PERCENTAGE | FIXED
    private Double expectedCommissionAmount; // calculated at response time

    // Parties
    private Long sharedById;
    private String sharedByName;            // Super Admin full name
    private Long agencyAdminId;
    private String agencyAdminName;         // Agency Admin full name

    private String message;
    private String rejectionReason;

    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
}
