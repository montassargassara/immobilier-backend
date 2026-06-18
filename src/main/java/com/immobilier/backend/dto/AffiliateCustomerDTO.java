package com.immobilier.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A buyer brought by an affiliate (CRM lead — never a User account).
 * Backed by {@code AffiliateCustomerRelation}.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AffiliateCustomerDTO {
    private Long          id;
    private Long          affiliateId;
    private String        affiliateName;
    private String        affiliateEmail;

    private String        buyerName;
    private String        buyerEmail;
    private String        buyerPhone;

    private Long          propertyId;
    private String        propertyTitle;
    private String        transactionType;   // SALE | RENT
    private Double        propertyPrice;

    private Double        commissionAmount;
    private Boolean       commissionPaid;
    private String        offerStatus;   // canonical deal lifecycle from SaleOffer (PENDING/ACCEPTED/COMPLETED/...)
    private LocalDateTime createdAt;
}
