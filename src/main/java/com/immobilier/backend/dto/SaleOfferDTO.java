package com.immobilier.backend.dto;

import com.immobilier.backend.enums.SaleOfferStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SaleOfferDTO {
    private Long id;

    // Affiliate
    private Long affiliateId;
    private String affiliateName;
    private String affiliateEmail;

    // Property snapshot
    private Long propertyId;
    private String propertyTitle;
    private String propertyAdresse;
    private String propertyCity;
    private Double propertyPrixVente;
    private Double propertyPrixLocation;
    private Double propertyCommissionPercentage;
    private String propertyCommissionType;
    private String mainImageUrl;

    // Buyer info
    private String buyerName;
    private String buyerEmail;
    private String buyerPhone;
    private Double offeredPrice;
    private String message;

    // Workflow
    private SaleOfferStatus status;
    private String rejectionReason;
    private Long respondedById;
    private String respondedByName;
    private LocalDateTime respondedAt;

    // Commission snapshot
    private Double commissionPercentage;
    private Double commissionAmount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
